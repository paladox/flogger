/*
 * Copyright (C) 2018 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.flogger.backend.log4j2;

import com.google.common.flogger.LogSite;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.SimpleMessageFormatter.SimpleLogHandler;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.util.Throwables;
import org.apache.logging.log4j.message.SimpleMessage;

/** Class that represents a log entry that can be written to log4j2. */
final class SimpleLog4j2LogEvent implements SimpleLogHandler {
  /** Creates a {@link SimpleLog4j2LogEvent} for a normal log statement from the given data. */
  static SimpleLog4j2LogEvent create(Logger logger, LogData data) {
    return new SimpleLog4j2LogEvent(logger, data);
  }

  /** Creates a {@link SimpleLog4j2LogEvent} in the case of an error during logging. */
  static SimpleLog4j2LogEvent error(Logger logger, RuntimeException error, LogData data) {
    return new SimpleLog4j2LogEvent(logger, error, data);
  }

  private final Logger logger;
  private final LogData logData;

  private Level level;
  private String message;
  private Throwable thrown;

  private SimpleLog4j2LogEvent(Logger logger, LogData logData) {
    this.logger = logger;
    this.logData = logData;
    Log4j2LogDataFormatter.format(logData, this);
  }

  private SimpleLog4j2LogEvent(Logger logger, RuntimeException error, LogData badLogData) {
    this.logger = logger;
    this.logData = badLogData;
    Log4j2LogDataFormatter.formatBadLogData(error, badLogData, this);
  }

  @Override
  public void handleFormattedLogMessage(
      java.util.logging.Level level, String message, Throwable thrown) {
    this.level = Log4j2LoggerBackend.toLog4jLevel(level);
    this.message = message;
    this.thrown = thrown;
  }

  Level getLevel() {
    return level;
  }

  LogEvent asLoggingEvent() {
    // The fully qualified class name of the logger instance is normally used to compute the log
    // location (file, class, method, line number) from the stacktrace. Since we already have the
    // log location in hand we don't need this computation. By passing in null as fully qualified
    // class name of the logger instance we ensure that the log location computation is disabled.
    // this is important since the log location computation is very expensive.
    String fqnOfCategoryClass = null;

    // The Nested Diagnostic Context (NDC) allows to include additional metadata into logs which
    // are written from the current thread.
    //
    // Example:
    //  NDC.push("user.id=" + userId);
    //  // do business logic that triggers logs
    //  NDC.pop();
    //  NDC.remove();
    //
    // By using '%x' in the ConversionPattern of an appender this data can be included in the logs.
    //
    // We could include this data here by doing 'NDC.get()', but we don't want to encourage people
    // using the log4j specific NDC. Instead this should be supported by a LoggingContext and usage
    // of Flogger tags.
    String nestedDiagnosticContext = "";

    // The Mapped Diagnostic Context (MDC) allows to include additional metadata into logs which
    // are written from the current thread.
    //
    // Example:
    //  MDC.put("user.id", userId);
    //  // do business logic that triggers logs
    //  MDC.clear();
    //
    // By using '%X{key}' in the ConversionPattern of an appender this data can be included in the
    // logs.
    //
    // We could include this data here by doing 'MDC.getContext()', but we don't want to encourage
    // people using the log4j specific MDC. Instead this should be supported by a LoggingContext and
    // usage of Flogger tags.
    Map<String, String> mdcProperties = Collections.emptyMap();

    final LogEvent event =
        Log4jLogEvent.newBuilder()
            .setLoggerName(log.toString())
            .setLoggerFqcn(fqnOfCategoryClass)
            .setLevel(level)
            .setMessage(new SimpleMessage(message))
            .setThreadName(Thread.currentThread().getName())
            .setTimeMillis(TimeUnit.NANOSECONDS.toMillis(logData.getTimestampNanos()))
            .setThrown(thrown != null ? new Throwables().getRootCause(thrown) : null)
            .setContextMap(mdcProperties)
            .build();

    return event;
  }

  @Override
  public String toString() {
    // Note that this toString() method is _not_ safe against exceptions thrown by user toString().
    StringBuilder out = new StringBuilder();
    out.append(getClass().getSimpleName()).append(" {\n  message: ").append(message).append('\n');
    Log4j2LogDataFormatter.appendLogData(logData, out);
    out.append("\n}");
    return out.toString();
  }
}