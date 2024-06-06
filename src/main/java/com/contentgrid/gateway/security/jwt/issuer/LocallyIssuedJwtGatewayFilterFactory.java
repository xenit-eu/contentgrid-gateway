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
    private final Function<String, JwtClaimsResolver> claimsResolverLocator;
    private final Function<String, JwtClaimsSigner> signerLocator;

    public LocallyIssuedJwtGatewayFilterFactory(
            Function<String, JwtClaimsResolver> claimsResolverLocator,
            Function<String, JwtClaimsSigner> signerLocator
    ) {
        super(Config.class);
        this.claimsResolverLocator = claimsResolverLocator;
        this.signerLocator = signerLocator;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("signer");
    }

    @Override
    public GatewayFilter apply(Config config) {
        var claimsResolver = StringUtils.hasText(config.getClaimsResolver())?
                claimsResolverLocator.apply(config.getClaimsResolver()):
                JwtClaimsResolver.empty();

        var signer = signerLocator.apply(config.getSigner());

        Assert.notNull(signer, "No signer found with name %s".formatted(config.getSigner()));

        return new LocallyIssuedJwtGatewayFilter(new SignedJwtIssuer(signer, claimsResolver)) {
            @Override
            public String toString() {
                return GatewayToStringStyler.filterToStringCreator(this)
                        .append("signer", config.getSigner())
                        .append("claimsResolver", StringUtils.hasText(config.getClaimsResolver())?claimsResolver:"(none)")
                        .toString();
            }
        };
    }

    @Data
    public static class Config {
        private String signer;
        private String claimsResolver;
    }
}
