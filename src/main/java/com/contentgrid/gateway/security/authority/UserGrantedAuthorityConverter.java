package com.contentgrid.gateway.security.authority;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

@RequiredArgsConstructor
public class UserGrantedAuthorityConverter implements
        Converter<Jwt, Collection<GrantedAuthority>>, GrantedAuthoritiesMapper {

    private final Converter<ClaimAccessor, Actor> actorConverter;

    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {
        return createFromClaimAccessor(source)
                .map(List::of)
                .orElseGet(List::of);
    }

    private Optional<GrantedAuthority> createFromClaimAccessor(ClaimAccessor claimAccessor) {
        return Optional.ofNullable(actorConverter.convert(claimAccessor))
                .map(PrincipalAuthenticationDetailsGrantedAuthority::new);
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        return Stream.concat(
                authorities.stream(),
                authorities.stream()
                        .filter(OidcUserAuthority.class::isInstance)
                        .map(OidcUserAuthority.class::cast)
                        .map(OidcUserAuthority::getIdToken)
                        .map(this::createFromClaimAccessor)
                        .flatMap(Optional::stream)
        ).toList();
    }
}
