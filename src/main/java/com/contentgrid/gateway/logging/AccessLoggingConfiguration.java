package com.contentgrid.gateway.logging;

import org.springframework.boot.reactor.netty.NettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.ReactorNetty;
import reactor.netty.http.server.logging.AccessLog;
import reactor.netty.http.server.logging.AccessLogFactory;

@Configuration(proxyBeanMethods = false)
class AccessLoggingConfiguration {

    @Bean
    NettyServerCustomizer accessLogServerCustomizer() {
        return server -> server.accessLog(accessLogEnabled(), accessLogFactory());
    }

    private static AccessLogFactory accessLogFactory() {
        // The format is constant so request URI braces can never become format placeholders.
        return args -> AccessLog.create("{}", HttpAccessLog.from(args));
    }

    static boolean accessLogEnabled() {
        return Boolean.parseBoolean(System.getProperty(ReactorNetty.ACCESS_LOG_ENABLED, "false"));
    }
}
