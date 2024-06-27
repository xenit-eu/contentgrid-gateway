package com.contentgrid.gateway.runtime.security.authority;

import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.DelegatedAuthenticationDetailsGrantedAuthority;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.TextEncryptorFactory;
import com.nimbusds.jwt.JWTClaimsSet;
import java.text.ParseException;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.jwt.Jwt;

@RequiredArgsConstructor
public class ExtensionDelegationGrantedAuthorityConverter implements
        Converter<Jwt, Collection<GrantedAuthority>> {

    private final TextEncryptorFactory encryptorFactory;
    private final Converter<ClaimAccessor, Actor> actorConverter;

    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {
        var principal = actorConverter.convert(decryptClaims(source.getClaimAsString("restrict:principal_claims")));
        if (principal == null) {
            return null;
        }
        var actor = actorConverter.convert(() -> source.getClaimAsMap("act"));
        if (actor == null) {
            return null;
        }
        return List.of(new DelegatedAuthenticationDetailsGrantedAuthority(principal, actor));
    }

    @SneakyThrows(ParseException.class)
    private ClaimAccessor decryptClaims(String encryptedClaims) {
        var decryptedClaimsString = encryptorFactory.newEncryptor().decrypt(encryptedClaims);
        var claimSet = JWTClaimsSet.parse(decryptedClaimsString);
        return claimSet::toJSONObject;
    }

}
