# AsyncSimpleLog
![Current version](https://img.shields.io/maven-metadata/v?label=current%20version&metadataUrl=https%3A%2F%2Frepo.unascribed.com%2Fcom%2Funascribed%2Fasyncsimplelog%2Fmaven-metadata.xml&style=flat-square)

AsyncSimpleLog is an asynchronous implementation of a SLF4j Logger similar
to SLF4j's SimpleLogger. Naive logger implementations (including SimpleLogger)
or just direct System.out prints will synchronize on the output stream,
resulting in a point of contention in multithreaded applications.

It was written in 2016 for a highly parallel HTTP server based on
Project Grizzly, but it is clean and useful enough that I have been carrying
it with me to any project that needs a logger, which is most of them. Logback
is simply too heavy for me most of the time, and the ability to silence log
lines by regex and automatically collapse repeat lines is helpful.

It has a variety of other features it has grown over the years as well, including
ANSI color/Powerline character support, silencing of loggers by their owning
classes (useful for shutting up servlet engines), and a couple other oddities.

It has a very simple implementation with few bells and whistles, and is configured
purely through code. It's up to you to expose these features through a config
file, environment variables, etc.

## Installation
AsyncSimpleLog is distributed on my personal Maven, repo.unascribed.com. Here's
how you can use it in Gradle:

```gradle
repositories {
	maven {
		url "https://repo.unascribed.com"
	}
}
```

And you can add AsyncSimpleLog as a dependency as such:

```gradle
dependencies {
	implementation "com.unascribed:asyncsimplelog:4.6.0"
}
```

## Usage
AsyncSimpleLog comes with a SLF4j StaticLoggerBinder. As long as slf4j-api is on
your classpath, it'll work out of the box. However, AsyncSimpleLog **must** be
initialized by calling `AsyncSimpleLog.startLogging()` to start the logging
thread during your application's initialization.

## Maturity
I've been using AsyncSimpleLog as my preferred SLF4j logger implementation since
I wrote it in 2016. It's seen quite a bit of use as such and is ready to go.
