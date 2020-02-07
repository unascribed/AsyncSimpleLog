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

import java.util.Date;
import org.slf4j.Marker;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.LocationAwareLogger;

import com.unascribed.asyncsimplelog.AsyncSimpleLog.LogLevel;
import com.unascribed.asyncsimplelog.AsyncSimpleLog.LogRecord;

class LoggerImpl implements LocationAwareLogger {
	static int minLogLevel = INFO_INT;

	private final String name;
	private final String shortName;

	public LoggerImpl(String name) {
		this.name = name;
		String shortNameTmp = "";
		try {
			Class.forName(name, false, getClass().getClassLoader());
			// it's a class name, truncate it
			shortNameTmp = name.substring(name.lastIndexOf('.')+1, name.length());
		} catch (ClassNotFoundException e) {
			// it's custom, don't touch it
			shortNameTmp = name;
		}
		this.shortName = shortNameTmp;
	}

	public String getShortName() {
		return shortName;
	}

	private void log(int level, String message, Throwable t) {
		log(null, level, message, t);
	}

	private void log(Marker marker, int level, String message, Throwable t) {
		if (!isLevelEnabled(level)) {
			return;
		}

		AsyncSimpleLog.enqueue(new LogRecord(this, new Date(), LogLevel.fromLevelInt(level), message, t));
	}

	private void formatAndLog(int level, String format, Object arg1, Object arg2) {
		formatAndLog(null, level, format, arg1, arg2);
	}


	private void formatAndLog(int level, String format, Object[] arguments) {
		formatAndLog(null, level, format, arguments);
	}

	private void formatAndLog(Marker marker, int level, String format, Object arg1, Object arg2) {
		if (!isLevelEnabled(level)) {
			return;
		}
		FormattingTuple tp = MessageFormatter.format(format, arg1, arg2);
		log(marker, level, tp.getMessage(), tp.getThrowable());
	}

	private void formatAndLog(Marker marker, int level, String format, Object[] arguments) {
		if (!isLevelEnabled(level)) {
			return;
		}
		FormattingTuple tp = MessageFormatter.arrayFormat(format, arguments);
		log(marker, level, tp.getMessage(), tp.getThrowable());
	}


	protected boolean isLevelEnabled(int logLevel) {
		return logLevel >= minLogLevel;
	}

	@Override
	public void log(Marker marker, String fqcn, int level, String message, Object[] argArray, Throwable t) {
		if (t != null) {
			Object[] nw;
			if (argArray != null) {
				nw = new Object[argArray.length+1];
				System.arraycopy(argArray, 0, nw, 0, argArray.length);
			} else {
				nw = new Object[1];
			}
			nw[nw.length-1] = t;
		}
		formatAndLog(marker, level, message, argArray);
	}


	// Begin extremely spammy Logger implementation (ugh)

	@Override
	public boolean isTraceEnabled() {
		return isLevelEnabled(TRACE_INT);
	}

	@Override
	public void trace(String msg) {
		log(TRACE_INT, msg, null);
	}

	@Override
	public void trace(String format, Object arg1) {
		formatAndLog(TRACE_INT, format, arg1, null);
	}

	@Override
	public void trace(String format, Object arg1, Object arg2) {
		formatAndLog(TRACE_INT, format, arg1, arg2);
	}

	@Override
	public void trace(String format, Object... argArray) {
		formatAndLog(TRACE_INT, format, argArray);
	}

	@Override
	public void trace(String msg, Throwable t) {
		log(TRACE_INT, msg, t);
	}

	@Override
	public boolean isDebugEnabled() {
		return isLevelEnabled(DEBUG_INT);
	}

	@Override
	public void debug(String msg) {
		log(DEBUG_INT, msg, null);
	}

	@Override
	public void debug(String format, Object arg1) {
		formatAndLog(DEBUG_INT, format, arg1, null);
	}

	@Override
	public void debug(String format, Object arg1, Object arg2) {
		formatAndLog(DEBUG_INT, format, arg1, arg2);
	}

	@Override
	public void debug(String format, Object... argArray) {
		formatAndLog(DEBUG_INT, format, argArray);
	}

	@Override
	public void debug(String msg, Throwable t) {
		log(DEBUG_INT, msg, t);
	}

	@Override
	public boolean isInfoEnabled() {
		return isLevelEnabled(INFO_INT);
	}

	@Override
	public void info(String msg) {
		log(INFO_INT, msg, null);
	}

	@Override
	public void info(String format, Object arg) {
		formatAndLog(INFO_INT, format, arg, null);
	}

	@Override
	public void info(String format, Object arg1, Object arg2) {
		formatAndLog(INFO_INT, format, arg1, arg2);
	}

	@Override
	public void info(String format, Object... argArray) {
		formatAndLog(INFO_INT, format, argArray);
	}

	@Override
	public void info(String msg, Throwable t) {
		log(INFO_INT, msg, t);
	}

	@Override
	public boolean isWarnEnabled() {
		return isLevelEnabled(WARN_INT);
	}

	@Override
	public void warn(String msg) {
		log(WARN_INT, msg, null);
	}

	@Override
	public void warn(String format, Object arg) {
		formatAndLog(WARN_INT, format, arg, null);
	}

	@Override
	public void warn(String format, Object arg1, Object arg2) {
		formatAndLog(WARN_INT, format, arg1, arg2);
	}

	@Override
	public void warn(String format, Object... argArray) {
		formatAndLog(WARN_INT, format, argArray);
	}

	@Override
	public void warn(String msg, Throwable t) {
		log(WARN_INT, msg, t);
	}

	@Override
	public boolean isErrorEnabled() {
		return isLevelEnabled(ERROR_INT);
	}

	@Override
	public void error(String msg) {
		log(ERROR_INT, msg, null);
	}

	@Override
	public void error(String format, Object arg) {
		formatAndLog(ERROR_INT, format, arg, null);
	}

	@Override
	public void error(String format, Object arg1, Object arg2) {
		formatAndLog(ERROR_INT, format, arg1, arg2);
	}

	@Override
	public void error(String format, Object... argArray) {
		formatAndLog(ERROR_INT, format, argArray);
	}

	@Override
	public void error(String msg, Throwable t) {
		log(ERROR_INT, msg, t);
	}

	@Override
	public boolean isTraceEnabled(Marker marker) {
		return isLevelEnabled(TRACE_INT);
	}

	@Override
	public void trace(Marker marker, String msg) {
		log(marker, TRACE_INT, msg, null);
	}

	@Override
	public void trace(Marker marker, String format, Object arg) {
		formatAndLog(marker, TRACE_INT, format, arg, null);
	}

	@Override
	public void trace(Marker marker, String format, Object... argArray) {
		formatAndLog(marker, TRACE_INT, format, argArray);
	}

	@Override
	public void trace(Marker marker, String msg, Throwable t) {
		log(marker, TRACE_INT, msg, t);
	}

	@Override
	public void trace(Marker marker, String format, Object arg1, Object arg2) {
		formatAndLog(marker, TRACE_INT, format, arg1, arg2);
	}

	@Override
	public boolean isDebugEnabled(Marker marker) {
		return isLevelEnabled(DEBUG_INT);
	}

	@Override
	public void debug(Marker marker, String msg) {
		log(marker, DEBUG_INT, msg, null);
	}

	@Override
	public void debug(Marker marker, String format, Object arg) {
		formatAndLog(marker, DEBUG_INT, format, arg, null);
	}

	@Override
	public void debug(Marker marker, String format, Object... argArray) {
		formatAndLog(marker, DEBUG_INT, format, argArray);
	}

	@Override
	public void debug(Marker marker, String msg, Throwable t) {
		log(marker, DEBUG_INT, msg, t);
	}

	@Override
	public void debug(Marker marker, String format, Object arg1, Object arg2) {
		formatAndLog(DEBUG_INT, format, arg1, arg2);
	}

	@Override
	public boolean isInfoEnabled(Marker marker) {
		return isLevelEnabled(INFO_INT);
	}

	@Override
	public void info(Marker marker, String msg) {
		log(marker, INFO_INT, msg, null);
	}

	@Override
	public void info(Marker marker, String format, Object arg) {
		formatAndLog(marker, INFO_INT, format, arg, null);
	}

	@Override
	public void info(Marker marker, String format, Object... argArray) {
		formatAndLog(marker, INFO_INT, format, argArray);
	}

	@Override
	public void info(Marker marker, String msg, Throwable t) {
		log(marker, INFO_INT, msg, t);
	}

	@Override
	public void info(Marker marker, String format, Object arg1, Object arg2) {
		formatAndLog(marker, INFO_INT, format, arg1, arg2);
	}

	@Override
	public boolean isWarnEnabled(Marker marker) {
		return isLevelEnabled(WARN_INT);
	}

	@Override
	public void warn(Marker marker, String msg) {
		log(marker, WARN_INT, msg, null);
	}

	@Override
	public void warn(Marker marker, String format, Object arg) {
		formatAndLog(marker, WARN_INT, format, arg, null);
	}

	@Override
	public void warn(Marker marker, String format, Object... argArray) {
		formatAndLog(marker, WARN_INT, format, argArray);
	}

	@Override
	public void warn(Marker marker, String msg, Throwable t) {
		log(marker, WARN_INT, msg, t);
	}

	@Override
	public void warn(Marker marker, String format, Object arg1, Object arg2) {
		formatAndLog(marker, WARN_INT, format, arg1, arg2);
	}

	@Override
	public boolean isErrorEnabled(Marker marker) {
		return isLevelEnabled(ERROR_INT);
	}

	@Override
	public void error(Marker marker, String msg) {
		log(marker, ERROR_INT, msg, null);
	}

	@Override
	public void error(Marker marker, String format, Object arg) {
		formatAndLog(marker, ERROR_INT, format, arg, null);
	}

	@Override
	public void error(Marker marker, String format, Object... argArray) {
		formatAndLog(marker, ERROR_INT, format, argArray);
	}

	@Override
	public void error(Marker marker, String msg, Throwable t) {
		log(marker, ERROR_INT, msg, t);
	}

	@Override
	public void error(Marker marker, String format, Object arg1, Object arg2) {
		formatAndLog(marker, ERROR_INT, format, arg1, arg2);
	}

	@Override
	public String getName() {
		return name;
	}

}