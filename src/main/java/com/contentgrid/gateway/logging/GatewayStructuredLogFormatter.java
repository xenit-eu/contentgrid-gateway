package com.contentgrid.gateway.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

/**
 * Formats application and access log events as newline-delimited JSON.
 */
public class GatewayStructuredLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

    private static final String ACCESS_LOG_LOGGER_NAME = "reactor.netty.http.server.AccessLog";

    private static final JsonWriter<ILoggingEvent> JSON_WRITER = JsonWriter
            .of(GatewayStructuredLogFormatter::addApplicationMembers)
            .withNewLineAtEnd();

    private static final JsonWriter<HttpAccessLog> ACCESS_JSON_WRITER = JsonWriter
            .of(GatewayStructuredLogFormatter::addAccessMembers)
            .withNewLineAtEnd();

    @Override
    public String format(ILoggingEvent event) {
        HttpAccessLog accessLog = accessLog(event);
        if (accessLog != null) {
            return ACCESS_JSON_WRITER.writeToString(accessLog);
        }
        return JSON_WRITER.writeToString(event);
    }

    private static @Nullable HttpAccessLog accessLog(ILoggingEvent event) {
        if (!ACCESS_LOG_LOGGER_NAME.equals(event.getLoggerName())) {
            return null;
        }
        Object[] arguments = event.getArgumentArray();
        if (arguments == null || arguments.length != 1) {
            return null;
        }
        return arguments[0] instanceof HttpAccessLog accessLog ? accessLog : null;
    }

    private static void addApplicationMembers(JsonWriter.Members<ILoggingEvent> members) {
        members.add("timestamp", event -> BigDecimal.valueOf(event.getTimeStamp(), 3));
        members.add("type", "application");
        members.add("component", "contentgrid-gateway");
        members.add("loggerName", ILoggingEvent::getLoggerName);
        members.add("severity", event -> event.getLevel().toString());
        members.add("thread", ILoggingEvent::getThreadName);
        members.add("shortMessage", ILoggingEvent::getFormattedMessage);
        members.add("fullMessage", GatewayStructuredLogFormatter::fullMessage);
        members.add("context", GatewayStructuredLogFormatter::context).whenNotEmpty();
    }

    private static void addAccessMembers(JsonWriter.Members<HttpAccessLog> members) {
        members.add("timestamp", HttpAccessLog::timestamp);
        members.add("type", "access");
        members.add("component", "contentgrid-gateway");
        members.add("requestTime", HttpAccessLog::requestTime);
        members.add("responseStatus", HttpAccessLog::responseStatus);
        members.add("requestMethod", HttpAccessLog::requestMethod);
        members.add("requestUri", HttpAccessLog::requestUri);
        members.add("request", HttpAccessLog::request).whenNotNull();
        members.add("remoteAddr", HttpAccessLog::remoteAddr).whenNotNull();
    }

    private static String fullMessage(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy == null) {
            return message;
        }
        return message + "\n" + ThrowableProxyUtil.asString(throwableProxy);
    }

    private static Map<String, Object> context(ILoggingEvent event) {
        Map<String, Object> context = new LinkedHashMap<>(event.getMDCPropertyMap());
        List<KeyValuePair> keyValuePairs = event.getKeyValuePairs();
        if (keyValuePairs != null) {
            // Keep MDC values such as traceId when an event carries a key-value pair with the same key.
            keyValuePairs.forEach(pair -> context.putIfAbsent(pair.key, pair.value));
        }
        return context;
    }
}
