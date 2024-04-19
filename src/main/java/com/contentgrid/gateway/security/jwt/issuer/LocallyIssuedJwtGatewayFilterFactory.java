package com.contentgrid.gateway.security.jwt.issuer;

import com.contentgrid.gateway.security.jwt.issuer.LocallyIssuedJwtGatewayFilterFactory.Config;
import java.util.List;
import java.util.function.Function;
import lombok.Data;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.support.AbstractConfigurable;
import org.springframework.cloud.gateway.support.GatewayToStringStyler;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


public class LocallyIssuedJwtGatewayFilterFactory extends AbstractConfigurable<Config> implements
        GatewayFilterFactory<Config> {
    private final ApplicationContext applicationContext;
    private final Function<String, JwtClaimsSigner> signerLocator;

    public LocallyIssuedJwtGatewayFilterFactory(ApplicationContext applicationContext, Function<String, JwtClaimsSigner> signerLocator) {
        super(Config.class);
        this.applicationContext = applicationContext;
        this.signerLocator = signerLocator;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("signer");
    }

    @Override
    public GatewayFilter apply(Config config) {
        var claimsResolver = StringUtils.hasText(config.getClaimsResolverRef())?
                applicationContext.getBean(config.claimsResolverRef, JwtClaimsResolver.class):
                JwtClaimsResolver.empty();

        var signer = signerLocator.apply(config.getSigner());

        Assert.notNull(signer, "No signer found with name %s".formatted(config.getSigner()));

        return new LocallyIssuedJwtGatewayFilter(new SignedJwtIssuer(signer, claimsResolver)) {
            @Override
            public String toString() {
                return GatewayToStringStyler.filterToStringCreator(this)
                        .append("signer", config.getSigner())
                        .append("claimsResolver", StringUtils.hasText(config.getClaimsResolverRef())?claimsResolver:"(none)")
                        .toString();
            }
        };
    }

    @Data
    static class Config {
        private String signer;
        private String claimsResolverRef;
    }
}
