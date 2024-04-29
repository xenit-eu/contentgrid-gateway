package com.contentgrid.gateway.security.refresh;

import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@AutoConfiguration
public class AuthenticationRefresherAutoConfiguration {
    @Bean
    @Primary
    AuthenticationRefresher authenticationRefresher(List<AuthenticationRefresher> authenticationRefreshers) {
        return new CompositeAuthenticationRefresher(authenticationRefreshers);
    }

    @Bean
    AuthenticationRefresher usernamePasswordAuthenticationRefresher() {
        return new NoopAuthenticationRefresher(UsernamePasswordAuthenticationToken.class);
    }

    @Bean
    AuthenticationRefresher anonymousAuthenticationRefresher() {
        return new NoopAuthenticationRefresher(AnonymousAuthenticationToken.class);
    }
}
