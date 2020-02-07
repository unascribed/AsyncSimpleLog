/*
 * AsyncSimpleLog - a simple, fast, and pretty logger for SLF4j
 * Copyright (c) 2016 - 2020 Una Thompson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.unascribed.asyncsimplelog;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.regex.Pattern;

import org.slf4j.ILoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;
import org.slf4j.spi.LocationAwareLogger;

/**
 * AsyncSimpleLog is an asynchronous implementation of a SLF4j Logger similar
 * to SLF4j's SimpleLogger. Naive logger implementations (including SimpleLogger)
 * or just direct System.out prints will synchronize on the output stream,
 * resulting in a point of contention in multithreaded applications.
 * <p>
 * This implementation was written for a highly parallel HTTP server based on
 * Project Grizzly, but it is clean and useful enough that I have been carrying
 * it with me to any project that needs a logger, which is most of them. Logback
 * is simply too heavy for me most of the time, and the ability to silence log
 * lines by regex and automatically collapse repeat lines is helpful.
 */
public final class AsyncSimpleLog extends Thread {
	static class LogRecord {
		public final LoggerImpl owner;
		public final Date date;
		public final LogLevel lvl;
		public final Object content;
		public final Throwable exception;

		public LogRecord(LoggerImpl owner, Date date, LogLevel lvl, Object content, Throwable exception) {
			this.owner = owner;
			this.date = date;
			this.lvl = lvl;
			this.content = content;
			this.exception = exception;
		}
	}

	public enum LogLevel {
		TRACE("TRCE", 240, 246, LocationAwareLogger.TRACE_INT),
		DEBUG("DBUG", 244, 253, LocationAwareLogger.DEBUG_INT),
		INFO("INFO", 36, 15, LocationAwareLogger.INFO_INT),
		WARN("WARN", 203, 15, LocationAwareLogger.WARN_INT),
		ERROR("EROR", 197, 15, LocationAwareLogger.ERROR_INT),
		;
		private static final LogLevel[] VALUES = values();
		/*package*/ final char chr;
		/*package*/ final String str;
		/*package*/ final int bg;
		/*package*/ final int fg;
		/*package*/ final int levelInt;
		private LogLevel(String str, int bg, int fg, int levelInt) {
			this.chr = str.charAt(0);
			this.str = str;
			this.bg = bg;
			this.fg = fg;
			this.levelInt = levelInt;
		}
		/*package*/ static LogLevel fromLevelInt(int i) {
			return VALUES[i/10];
		}
	}

	private static final Pattern NEWLINE_PATTERN = Pattern.compile("\n", Pattern.LITERAL);

	private static AsyncSimpleLog inst;
	private static final BlockingQueue<LogRecord> queue = new LinkedBlockingDeque<>();
	private static volatile boolean stop = false;
	private static final Set<Pattern> silenced = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private static final Set<Pattern> banned = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private static boolean ansi = false;
	private static boolean powerline = false;
	private static boolean collapseRepeats = false;

	private final PrintStream out;
	private final DateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");
	private final Map<String, Integer> hashes = new HashMap<>();
	private String lastLine;
	private String lastLineOwner;
	private int repeats = 0;
	private int longestShortName = 12;

	private long lastLogPrint = 0;
	private int consequtiveDeltaPrints = 0;

	private boolean flop;

	private final Thread shutdownHook;

	private AsyncSimpleLog() {
		super("AsyncSimpleLog thread");
		setDaemon(true);
		out = new PrintStream(System.out, false);
		shutdownHook = new Thread(() -> {
			stopLogging();
			while (isAlive()) {
				try {
					join();
				} catch (Throwable t) {}
			}
			List<LogRecord> li = new ArrayList<>();
			queue.drainTo(li);
			for (LogRecord lr : li) {
				printRecord(lr);
			}
			out.println();
			out.flush();
		}, "AsyncSimpleLog shutdown thread");
	}

	@Override
	public void run() {
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		try {
			while (!stop) {
				LogRecord lr = null;
				try {
					lr = queue.take();
					printRecord(lr);
					Thread.yield();
				} catch (InterruptedException e) {}
			}
			out.flush();
		} finally {
			try {
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			} catch (IllegalStateException ise) {}
		}
	}

	private void printRecord(LogRecord lr) {
		String str = Objects.toString(lr.content);
		for (Pattern p : silenced) {
			if (p.matcher(str).find()) {
				return;
			}
		}
		String name = lr.owner.getName();
		for (Pattern p : banned) {
			if (p.matcher(name).find()) {
				return;
			}
		}
		String shortName = lr.owner.getShortName();
		longestShortName = Math.max(longestShortName, shortName.length());
		if (collapseRepeats && lastLine != null && lastLineOwner.equals(lr.owner.getName()) && lastLine.equals(str) && lr.exception == null) {
			repeats++;
			if (ansi) {
				if (repeats == 1) {
					out.print(" ");
					out.print(ansiReset());
					out.print(ansiFg(141));
					if (powerline) {
						out.print("");
					}
					out.print(ansiBg(141));
					out.print(ansiFg(16));
					out.print(" ");
					out.print("repeated");
					out.print(" ");
					out.print(ansiSaveCursor());
				} else {
					out.print("\r");
					out.print(ansiReset());
					if (powerline) {
						out.print(ansiBg(16));
						out.print(ansiFg(248));
						out.print(" ");
					} else {
						out.print(ansiFg(8));
					}
					out.print(formatDate(lr.date));
					out.print(ansiRestoreCursor());
				}
				out.print(repeats);
				out.print(" time");
				if (repeats > 1) {
					out.print("s");
				}
				out.print(" ");
				if (powerline) {
					out.print(ansiReset());
					out.print(ansiFg(141));
					out.print("");
				}
			} else {
				if (repeats == 1) {
					out.print("[            ] [");
					out.print(lr.lvl.chr);
					out.print("/");
					out.print(shortName);
					out.print("] (last line repeats");
				}
				out.print(".");
			}
			out.flush();
		} else {
			if (repeats > 0) {
				if (ansi) {
					out.print(ansiReset());
				} else {
					out.println(")");
				}
			}
			flop = !flop;
			repeats = 0;
			lastLine = str;
			lastLineOwner = lr.owner.getName();
			if (ansi && collapseRepeats) {
				out.println();
			}
			if (lr.exception != null) lr.exception.printStackTrace(out);
			String dateStr = formatDate(lr.date);
			int indentAmt = 0;
			if (ansi) {
				out.print(ansiReset());
				if (powerline) {
					out.print(ansiBg(16));
					out.print(ansiFg(248));
					out.print(" ");
					indentAmt += 1;
				} else {
					out.print(ansiFg(8));
				}
				out.print(dateStr); indentAmt += dateStr.length();
				out.print(" "); indentAmt += 1;
				if (powerline) {
					out.print(ansiBg(16));
					out.print(ansiFg(lr.lvl.bg));
					out.print("");
					indentAmt += 1;
				}
				out.print(ansiBg(lr.lvl.bg));
				out.print(ansiFg(lr.lvl.fg));
				if (!powerline) {
					out.print(" ");
					indentAmt += 1;
				}
				out.print(lr.lvl.str); indentAmt += lr.lvl.str.length();
				if (!powerline) {
					out.print(" ");
					indentAmt += 1;
				}
				int hash = hashes.computeIfAbsent(lr.owner.getName(), (s) -> murmur32(s));
				int code = (Math.abs(hash)%36)+124;
				int fgLight = 231;
				int fgDark = 16;
				if (lr.lvl == LogLevel.TRACE || lr.lvl == LogLevel.DEBUG) {
					code = flop ? 238 : 236;
					fgLight = 247;
					fgDark = 247;
				} else if (lr.lvl == LogLevel.ERROR) {
					code += 72;
				} else if (flop) {
					code += 36;
				}
				out.print(ansiBg(code));
				if (powerline) {
					out.print(ansiFg(lr.lvl.bg));
					out.print("");
					indentAmt += 1;
				}

				// this modulo trick is mostly right due to how the colors are
				// assigned, but there's a few colors that don't match those
				// rules - there's few of them, so just put in explicit exceptions
				if (code % 36 > 18 && code != 178 && code != 179 && code != 143 | code == 160 || code == 125 || code == 162 || code == 126) {
					out.print(ansiFg(fgLight));
				} else {
					out.print(ansiFg(fgDark));
				}
				out.print(" "); indentAmt += 1;
				for (int i = shortName.length(); i < longestShortName; i++) {
					out.print(" "); indentAmt += 1;
				}
				out.print(shortName); indentAmt += shortName.length();
				out.print(" "); indentAmt += 1;
				out.print(ansiReset());
				if (powerline) {
					out.print(ansiFg(code));
					out.print("");
					out.print(ansiReset());
					indentAmt += 1;
				}
				out.print(" "); indentAmt += 1;
				// when recoloring the foreground but not the background, use basic colors
				// that way we honor terminal color schemes and we won't make things illegible on black-on-white schemes
				if (lr.lvl == LogLevel.TRACE || lr.lvl == LogLevel.DEBUG) {
					out.print(ansiFg(8));
				} else if (lr.lvl == LogLevel.WARN) {
					out.print(ansiFg(11));
				} else if (lr.lvl == LogLevel.ERROR) {
					out.print(ansiFg(9));
				}
			} else {
				out.print("["); indentAmt += 1;
				out.print(dateStr); indentAmt += dateStr.length();
				out.print("] ["); indentAmt += 3;
				out.print(lr.lvl.chr); indentAmt += 1;
				out.print("/"); indentAmt += 1;
				out.print(shortName); indentAmt += shortName.length();
				out.print("] "); indentAmt += 2;
			}
			if (str.contains("\n")) {
				boolean first = true;
				for (String s : NEWLINE_PATTERN.split(str)) {
					if (first) {
						first = false;
					} else {
						out.println();
						for (int i = 0; i < indentAmt; i++) {
							out.print(" ");
						}
					}
					out.print(s);
				}
			} else {
				out.print(str);
			}
			if (ansi) {
				out.print(ansiReset());
			}
			if (!ansi || !collapseRepeats) {
				out.println();
			}
			out.flush();
		}
	}

	private static int murmur32(String s) {
		byte[] data = s.getBytes(StandardCharsets.UTF_8);
		int seed = 0x9747b28c;

		int m = 0x5bd1e995;
		int r = 24;

		int h = seed ^ data.length;
		int length4 = data.length / 4;

		for (int i = 0; i < length4; i++) {
			int i4 = i * 4;
			int k = (data[i4 + 0] & 0xff) + ((data[i4 + 1] & 0xff) << 8)
					+ ((data[i4 + 2] & 0xff) << 16)
					+ ((data[i4 + 3] & 0xff) << 24);
			k *= m;
			k ^= k >>> r;
			k *= m;
			h *= m;
			h ^= k;
		}

		switch (data.length % 4) {
			case 3: h ^= (data[(data.length & ~3) + 2] & 0xff) << 16;
			case 2: h ^= (data[(data.length & ~3) + 1] & 0xff) << 8;
			case 1:
				h ^= (data[data.length & ~3] & 0xff);
				h *= m;
		}

		h ^= h >>> 13;
		h *= m;
		h ^= h >>> 15;

		return h;
	}

	private String formatDate(Date date) {
		long d = date.getTime() - lastLogPrint;
		if (d > 1000 || consequtiveDeltaPrints > 10) {
			lastLogPrint = date.getTime();
			consequtiveDeltaPrints = 0;
			return df.format(date);
		}
		lastLogPrint = date.getTime();
		consequtiveDeltaPrints++;
		return "        +"+Long.toString(d+1000).substring(1);
	}

	private String ansiSaveCursor() {
		return "\u001B[s";
	}

	private String ansiRestoreCursor() {
		return "\u001B[u";
	}

	private String ansiReset() {
		return "\u001B[0m";
	}

	private String ansiFg(int i) {
		return "\u001B[38;5;"+i+"m";
	}

	private String ansiBg(int i) {
		return "\u001B[48;5;"+i+"m";
	}

	/*package*/ static void enqueue(LogRecord lr) {
		queue.add(lr);
	}

	/**
	 * Start the AsyncSimpleLog background thread and begin logging to stdout.
	 */
	public static void startLogging() {
		synchronized (AsyncSimpleLog.class) {
			if (inst != null) return;
			stop = false;
			inst = new AsyncSimpleLog();
			inst.start();
		}
	}

	/**
	 * Stop the AsyncSimpleLog background thread. Any remaining messages will
	 * remain queued.
	 */
	public static void stopLogging() {
		synchronized (AsyncSimpleLog.class) {
			if (inst == null) return;
			stop = true;
			inst.interrupt();
			inst = null;
		}
	}

	/**
	 * Drop any log lines that have a <em>message</em> matching the given regex.
	 */
	public static void silence(Pattern p) {
		silenced.add(p);
	}

	/**
	 * Drop any log lines that have a <em>full name</em> matching the given regex.
	 */
	public static void ban(Pattern p) {
		banned.add(p);
	}

	/**
	 * Set the minimum log level to the given level. Any messages from a more
	 * verbose level will be dropped before they are ever submitted to the
	 * background thread.
	 */
	public static void setMinLogLevel(LogLevel level) {
		if (level == null) throw new IllegalArgumentException("level must not be null");
		LoggerImpl.minLogLevel = level.levelInt;
	}

	/**
	 * Enable or disable the usage of ANSI escape sequences.
	 * @param ansi {@code true} to include ANSI escape sequences in output
	 */
	public static void setAnsi(boolean ansi) {
		AsyncSimpleLog.ansi = ansi;
	}

	/**
	 * Enable or disable the usage of Powerline private-use-area characters.
	 * @param powerline {@code true} to use Powerline PUA characters.
	 */
	public static void setPowerline(boolean powerline) {
		AsyncSimpleLog.powerline = powerline;
	}

	/**
	 * Enable or disable the collapsing of repeat log lines. This is
	 * incompatible with input and is known to be somewhat buggy in
	 * ANSI mode, and so it is off by default.
	 * @param collapseRepeats {@code true} to collapse repeat log lines
	 */
	public static void setCollapseRepeats(boolean collapseRepeats) {
		AsyncSimpleLog.collapseRepeats = collapseRepeats;
	}

	/**
	 * Internal method for use from {@link StaticLoggerBinder}.
	 */
	public static ILoggerFactory getLoggerFactory() {
		return (name) -> {
			for (Pattern p : banned) {
				if (p.matcher(name).find()) {
					return new BannedLogger(name);
				}
			}
			return new LoggerImpl(name);
		};
	}

}
