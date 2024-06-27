package com.contentgrid.gateway.runtime.security.jwt.issuer;

import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import com.contentgrid.gateway.security.jwt.issuer.NamedJwtClaimsResolver;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.PropertiesBasedTextEncryptorFactory;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.PropertiesBasedTextEncryptorFactory.TextEncryptorProperties;
import java.util.Random;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled")
public class RuntimeJwtInternalIssuerConfiguration {
    @Bean
    @NamedJwtClaimsResolver("apps")
    RuntimeJwtClaimsResolver runtimeJwtClaimsResolver() {
        return new RuntimeJwtClaimsResolver();
    }

    private static final String AUTHENTICATION_ENCRYPTION = "contentgrid.gateway.runtime-platform.endpoints.authentication.encryption";

    @Bean(name = AUTHENTICATION_ENCRYPTION)
    @ConfigurationProperties(AUTHENTICATION_ENCRYPTION)
    @ConditionalOnProperty(prefix = AUTHENTICATION_ENCRYPTION, name = "active-keys")
    TextEncryptorProperties authenticationTextEncryptorProperties() {
        return new TextEncryptorProperties();
    }

    @Bean
    @NamedJwtClaimsResolver("authentication")
    @ConditionalOnBean(name = AUTHENTICATION_ENCRYPTION)
    RuntimeAuthenticationJwtClaimsResolver authorizationJwtClaimsResolver(
            ApplicationConfigurationRepository applicationConfigurationRepository,
            @Qualifier(AUTHENTICATION_ENCRYPTION)
            TextEncryptorProperties encryptorProperties,
            ResourcePatternResolver resourcePatternResolver
    ) {
        return new RuntimeAuthenticationJwtClaimsResolver(applicationConfigurationRepository, new PropertiesBasedTextEncryptorFactory(resourcePatternResolver, encryptorProperties, new Random()));
    }
}
