package com.contentgrid.gateway.runtime.security.jwt.issuer;

import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.security.authority.ExtensionDelegationGrantedAuthorityConverter;
import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.authority.ActorConverterType;
import com.contentgrid.gateway.security.authority.AggregateActorConverter;
import com.contentgrid.gateway.security.jwt.issuer.NamedJwtClaimsResolver;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.PropertiesBasedTextEncryptorFactory;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.PropertiesBasedTextEncryptorFactory.TextEncryptorProperties;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.TextEncryptorFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.security.oauth2.core.ClaimAccessor;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled")
public class RuntimeJwtInternalIssuerConfiguration {
    @Bean
    @NamedJwtClaimsResolver("apps")
    RuntimeJwtClaimsResolver runtimeJwtClaimsResolver() {
        return new RuntimeJwtClaimsResolver();
    }

    private static final String AUTHENTICATION_ENCRYPTION = "contentgrid.gateway.runtime-platform.endpoints.authentication.encryption";
    private static final String AUTHENTICATION_ENCRYPTOR_FACTORY = AUTHENTICATION_ENCRYPTION + ".TextEncryptorFactory";

    @Bean(name = AUTHENTICATION_ENCRYPTION)
    @ConfigurationProperties(AUTHENTICATION_ENCRYPTION)
    @ConditionalOnProperty(prefix = AUTHENTICATION_ENCRYPTION, name = "active-keys")
    TextEncryptorProperties authenticationTextEncryptorProperties() {
        return new TextEncryptorProperties();
    }

    @Bean(name = AUTHENTICATION_ENCRYPTOR_FACTORY)
    @ConditionalOnBean(name = AUTHENTICATION_ENCRYPTION)
    TextEncryptorFactory authenticationTextEncryptorFactory(
            ResourcePatternResolver resourcePatternResolver,
            @Qualifier(AUTHENTICATION_ENCRYPTION)
            TextEncryptorProperties properties
    ) {
        return new PropertiesBasedTextEncryptorFactory(resourcePatternResolver, properties);
    }

    @Bean
    @NamedJwtClaimsResolver("authentication")
    @ConditionalOnBean(name = AUTHENTICATION_ENCRYPTOR_FACTORY)
    RuntimeAuthenticationJwtClaimsResolver authorizationJwtClaimsResolver(
            ApplicationConfigurationRepository applicationConfigurationRepository,
            @Qualifier(AUTHENTICATION_ENCRYPTOR_FACTORY)
            TextEncryptorFactory encryptionFactory
    ) {
        return new RuntimeAuthenticationJwtClaimsResolver(applicationConfigurationRepository, encryptionFactory);
    }

    @Bean
    @ConditionalOnBean(name = AUTHENTICATION_ENCRYPTOR_FACTORY)
    ExtensionDelegationGrantedAuthorityConverter runtimeExtensionDelegationGrantedAuthorityConverter(
            @ActorConverterType(ActorType.USER)
            Converter<ClaimAccessor, Actor> userActorConverter,
            @ActorConverterType(ActorType.EXTENSION)
            Converter<ClaimAccessor, Actor> extensionActorConverter,
            @Qualifier(AUTHENTICATION_ENCRYPTOR_FACTORY)
            TextEncryptorFactory encryptorFactory
    ) {
        return new ExtensionDelegationGrantedAuthorityConverter(encryptorFactory, new AggregateActorConverter(
                extensionActorConverter,
                userActorConverter
        ));
    }
}
