package com.contentgrid.gateway.logging;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.ZonedDateTime;
import org.jspecify.annotations.Nullable;
import reactor.netty.http.server.ConnectionInformation;
import reactor.netty.http.server.logging.AccessLogArgProvider;

/**
 * Immutable access log record. Reactor Netty reuses its argument provider, so the
 * values are materialized before the {@link reactor.netty.http.server.logging.AccessLog}
 * is created.
 */
public record HttpAccessLog(
        BigDecimal timestamp,
        BigDecimal requestTime,
        int responseStatus,
        String requestMethod,
        String requestUri,
        @Nullable String request,
        @Nullable String remoteAddr
) {

    public static HttpAccessLog from(AccessLogArgProvider args) {
        return new HttpAccessLog(
                timestamp(args.accessDateTime()),
                BigDecimal.valueOf(args.duration(), 3),
                responseStatus(args.status()),
                text(args.method()),
                text(args.uri()),
                request(args),
                remoteAddr(args.connectionInformation())
        );
    }

    private static BigDecimal timestamp(@Nullable ZonedDateTime accessDateTime) {
        long epochMillis = accessDateTime != null
                ? accessDateTime.toInstant().toEpochMilli()
                : System.currentTimeMillis();
        return BigDecimal.valueOf(epochMillis, 3);
    }

    private static int responseStatus(@Nullable CharSequence status) {
        return status != null ? Integer.parseInt(status.toString()) : 0;
    }

    private static String text(@Nullable CharSequence value) {
        return value != null ? value.toString() : "";
    }

    private static @Nullable String request(AccessLogArgProvider args) {
        CharSequence method = args.method();
        CharSequence uri = args.uri();
        if (method == null || uri == null) {
            return null;
        }
        String protocol = args.protocol();
        String request = method + " " + uri;
        return protocol != null ? request + " " + protocol : request;
    }

    private static @Nullable String remoteAddr(@Nullable ConnectionInformation connectionInformation) {
        if (connectionInformation == null) {
            return null;
        }
        SocketAddress remoteAddress = connectionInformation.remoteAddress();
        // Forwarded addresses are resolved by Netty's connection information; the port is not part of remoteAddr.
        return remoteAddress instanceof InetSocketAddress inetSocketAddress
                ? inetSocketAddress.getHostString()
                : null;
    }
}
