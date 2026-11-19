package com.contentgrid.gateway.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import io.netty.handler.codec.http.cookie.Cookie;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import reactor.netty.http.server.ConnectionInformation;
import reactor.netty.http.server.logging.AccessLogArgProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GatewayStructuredLogFormatterTest {

    private static final long TIMESTAMP = 1662025029085L;

    private final GatewayStructuredLogFormatter formatter = new GatewayStructuredLogFormatter();

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void writesTimestampAsNumericUnixSecondsWithMillisecondResolutionOnOneLine() {
        var json = this.formatter.format(event(Level.INFO, "test"));

        assertThat(json).startsWith("{").endsWith("}\n");
        assertThat(json.chars().filter(character -> character == '\n').count()).isEqualTo(1);
        assertThat(json).contains("\"timestamp\":1662025029.085");

        JsonNode timestamp = read(json).get("timestamp");
        assertThat(timestamp.isNumber()).isTrue();
        assertThat(timestamp.decimalValue()).isEqualByComparingTo("1662025029.085");
    }

    @Test
    void writesApplicationSchemaFields() {
        LoggingEvent event = event(Level.WARN, "hello {}", "world");

        JsonNode json = read(this.formatter.format(event));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "timestamp", "type", "component", "loggerName", "severity", "thread",
                "shortMessage", "fullMessage"
        );
        assertThat(json.get("type").asString()).isEqualTo("application");
        assertThat(json.get("component").asString()).isEqualTo("contentgrid-gateway");
        assertThat(json.get("loggerName").asString()).isEqualTo("com.contentgrid.gateway.test.Foo");
        assertThat(json.get("severity").asString()).isEqualTo("WARN");
        assertThat(json.get("thread").asString()).isEqualTo("test-thread");
        assertThat(json.get("shortMessage").asString()).isEqualTo("hello world");
        assertThat(json.get("fullMessage").asString()).isEqualTo("hello world");
    }

    @Test
    void appendsFullExceptionChainToFullMessageOnly() {
        var rootCause = new IllegalArgumentException("root-cause");
        var exception = new IllegalStateException("boom", rootCause);
        exception.addSuppressed(new UnsupportedOperationException("suppressed"));
        LoggingEvent event = event(Level.ERROR, "request failed");
        event.setThrowableProxy(new ThrowableProxy(exception));

        JsonNode json = read(this.formatter.format(event));

        assertThat(json.get("severity").asString()).isEqualTo("ERROR");
        assertThat(json.get("shortMessage").asString()).isEqualTo("request failed");
        assertThat(json.get("fullMessage").asString())
                .startsWith("request failed\n")
                .contains("java.lang.IllegalStateException: boom")
                .contains("Caused by: java.lang.IllegalArgumentException: root-cause")
                .contains("Suppressed: java.lang.UnsupportedOperationException: suppressed")
                .contains("GatewayStructuredLogFormatterTest");
    }

    @Test
    void omitsSourceFieldsAndDoesNotCollectCallerData() {
        LoggingEvent event = new LoggingEvent() {
            @Override
            public StackTraceElement[] getCallerData() {
                throw new AssertionError("caller data must not be collected");
            }

            @Override
            public boolean hasCallerData() {
                throw new AssertionError("caller data must not be collected");
            }
        };
        prepare(event, Level.INFO, "test");

        JsonNode json = read(this.formatter.format(event));

        assertThat(json.propertyNames())
                .doesNotContain("sourceClassName", "sourceLineNumber", "sourceMethodName", "callerData");
    }

    @Test
    void escapesQuotesBackslashesNewlinesControlCharactersAndUnicodeAsValidJson() {
        var message = "He said \"hi\" \\ at C:\\tmp\nnext\tline \u0001 café 😀";

        var json = this.formatter.format(event(Level.INFO, message));

        assertThat(json).contains("\\\"").contains("\\\\").contains("\\n").contains("\\t").contains("\\u0001");
        assertThat(json.chars().filter(character -> character == '\n').count()).isEqualTo(1);
        assertThat(read(json).get("shortMessage").asString()).isEqualTo(message);
    }

    @Test
    void writesAccessSnapshotAsOneAccessRecord() {
        var accessLog = HttpAccessLog.from(accessLogArgs(1021L, "503", "GET", "/hello?name=world", "HTTP/1.1",
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(TIMESTAMP), ZoneOffset.UTC),
                connectionInformation(new InetSocketAddress("203.0.113.7", 54321))));

        var json = this.formatter.format(accessLogEvent(accessLog));

        assertThat(json).startsWith("{").endsWith("}\n");
        assertThat(json.chars().filter(character -> character == '\n').count()).isEqualTo(1);
        JsonNode node = read(json);
        assertThat(node.propertyNames()).containsExactlyInAnyOrder(
                "timestamp", "type", "component", "requestTime", "responseStatus", "requestMethod",
                "requestUri", "request", "remoteAddr"
        );
        assertThat(node.get("timestamp").isNumber()).isTrue();
        assertThat(node.get("timestamp").decimalValue()).isEqualByComparingTo("1662025029.085");
        assertThat(node.get("type").asString()).isEqualTo("access");
        assertThat(node.get("component").asString()).isEqualTo("contentgrid-gateway");
        assertThat(node.get("requestTime").isNumber()).isTrue();
        assertThat(node.get("requestTime").decimalValue()).isEqualByComparingTo("1.021");
        assertThat(node.get("responseStatus").isIntegralNumber()).isTrue();
        assertThat(node.get("responseStatus").asInt()).isEqualTo(503);
        assertThat(node.get("requestMethod").asString()).isEqualTo("GET");
        assertThat(node.get("requestUri").asString()).isEqualTo("/hello?name=world");
        assertThat(node.get("request").asString()).isEqualTo("GET /hello?name=world HTTP/1.1");
        assertThat(node.get("remoteAddr").asString()).isEqualTo("203.0.113.7");
        assertThat(node.propertyNames()).doesNotContain(
                "loggerName", "severity", "thread", "shortMessage", "fullMessage",
                "sourceClassName", "sourceLineNumber", "sourceMethodName"
        );
    }

    @Test
    void omitsUnavailableAccessFieldsAndNonInetRemoteAddresses() {
        var unavailable = HttpAccessLog.from(accessLogArgs(0L, null, null, null, null, null, null));
        JsonNode unavailableJson = read(this.formatter.format(accessLogEvent(unavailable)));

        assertThat(unavailableJson.get("responseStatus").asInt()).isZero();
        assertThat(unavailableJson.propertyNames()).doesNotContain("request", "remoteAddr");

        var unixSocket = HttpAccessLog.from(accessLogArgs(1L, "200", "GET", "/", "HTTP/1.1",
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(TIMESTAMP), ZoneOffset.UTC),
                connectionInformation(new SocketAddress() {
                })));
        JsonNode unixSocketJson = read(this.formatter.format(accessLogEvent(unixSocket)));

        assertThat(unixSocketJson.get("remoteAddr")).isNull();
        assertThat(unixSocketJson.propertyNames()).doesNotContain("remoteAddr");
    }

    @Test
    void keepsPlainMessagesAndUnrelatedArgumentsAsApplicationRecords() {
        LoggingEvent plain = event(Level.INFO, "plain");
        LoggingEvent unrelated = event(Level.INFO, "{}", "not-a-snapshot");
        unrelated.setLoggerName("reactor.netty.http.server.AccessLog");
        var snapshot = HttpAccessLog.from(accessLogArgs(1L, "200", "GET", "/", "HTTP/1.1", null, null));
        LoggingEvent otherLogger = event(Level.INFO, "{}", snapshot);
        otherLogger.setLoggerName("com.example.AccessLog");

        assertApplicationRecord(plain, "plain");
        assertApplicationRecord(unrelated, "not-a-snapshot");
        assertApplicationRecord(otherLogger, snapshot.toString());
    }

    @Test
    void nestsMdcAndKeyValuePairsWithoutOverwritingSchemaFields() {
        LoggingEvent event = event(Level.INFO, "test");
        event.setMDCPropertyMap(Map.of("traceId", "mdc-trace", "timestamp", "not-a-timestamp"));
        event.addKeyValuePair(new KeyValuePair("traceId", "key-value-trace"));
        event.addKeyValuePair(new KeyValuePair("severity", "not-a-severity"));
        event.addKeyValuePair(new KeyValuePair("custom", 42));

        JsonNode json = read(this.formatter.format(event));

        assertThat(json.get("timestamp").isNumber()).isTrue();
        assertThat(json.get("severity").asString()).isEqualTo("INFO");
        assertThat(json.get("type").asString()).isEqualTo("application");
        assertThat(json.get("context").get("traceId").asString()).isEqualTo("mdc-trace");
        assertThat(json.get("context").get("timestamp").asString()).isEqualTo("not-a-timestamp");
        assertThat(json.get("context").get("severity").asString()).isEqualTo("not-a-severity");
        assertThat(json.get("context").get("custom").asInt()).isEqualTo(42);
    }

    private LoggingEvent accessLogEvent(HttpAccessLog accessLog) {
        LoggingEvent event = event(Level.INFO, "{}");
        event.setLoggerName("reactor.netty.http.server.AccessLog");
        event.setArgumentArray(new Object[] { accessLog });
        return event;
    }

    private AccessLogArgProvider accessLogArgs(long duration, String status, String method, String uri,
            String protocol, ZonedDateTime accessDateTime, ConnectionInformation connectionInformation) {
        return new AccessLogArgProvider() {
            @Override
            @Deprecated
            public String zonedDateTime() {
                return null;
            }

            @Override
            public ZonedDateTime accessDateTime() {
                return accessDateTime;
            }

            @Override
            @Deprecated
            public SocketAddress remoteAddress() {
                return null;
            }

            @Override
            public ConnectionInformation connectionInformation() {
                return connectionInformation;
            }

            @Override
            public CharSequence method() {
                return method;
            }

            @Override
            public CharSequence uri() {
                return uri;
            }

            @Override
            public String protocol() {
                return protocol;
            }

            @Override
            public String user() {
                return null;
            }

            @Override
            public CharSequence status() {
                return status;
            }

            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public long duration() {
                return duration;
            }

            @Override
            public CharSequence requestHeader(CharSequence name) {
                return null;
            }

            @Override
            public CharSequence responseHeader(CharSequence name) {
                return null;
            }

            @Override
            public Map<CharSequence, Set<Cookie>> cookies() {
                return null;
            }
        };
    }

    private ConnectionInformation connectionInformation(SocketAddress remoteAddress) {
        return new ConnectionInformation() {
            @Override
            public SocketAddress hostAddress() {
                return null;
            }

            @Override
            public SocketAddress connectionHostAddress() {
                return null;
            }

            @Override
            public SocketAddress remoteAddress() {
                return remoteAddress;
            }

            @Override
            public SocketAddress connectionRemoteAddress() {
                return remoteAddress;
            }

            @Override
            public String scheme() {
                return "http";
            }

            @Override
            public String connectionScheme() {
                return "http";
            }

            @Override
            public String hostName() {
                return "localhost";
            }

            @Override
            public int hostPort() {
                return 80;
            }
        };
    }

    private void assertApplicationRecord(LoggingEvent event, String shortMessage) {
        JsonNode json = read(this.formatter.format(event));
        assertThat(json.get("type").asString()).isEqualTo("application");
        assertThat(json.get("shortMessage").asString()).isEqualTo(shortMessage);
        assertThat(json.propertyNames()).doesNotContain("requestTime", "responseStatus", "requestUri", "remoteAddr");
    }

    private LoggingEvent event(Level level, String message, Object... arguments) {
        var event = new LoggingEvent();
        prepare(event, level, message, arguments);
        return event;
    }

    private void prepare(LoggingEvent event, Level level, String message, Object... arguments) {
        event.setLoggerContext((LoggerContext) LoggerFactory.getILoggerFactory());
        event.setLoggerName("com.contentgrid.gateway.test.Foo");
        event.setLevel(level);
        event.setThreadName("test-thread");
        event.setInstant(Instant.ofEpochMilli(TIMESTAMP));
        event.setMessage(message);
        if (arguments.length > 0) {
            event.setArgumentArray(arguments);
        }
    }

    private JsonNode read(String json) {
        return this.jsonMapper.readTree(json);
    }
}
