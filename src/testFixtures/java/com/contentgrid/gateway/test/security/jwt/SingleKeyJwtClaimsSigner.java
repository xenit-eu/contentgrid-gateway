package com.contentgrid.gateway.test.security.jwt;

import com.contentgrid.gateway.security.jwt.issuer.JwtClaimsSigner;
import com.contentgrid.gateway.test.security.CryptoTestUtils;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.factories.DefaultJWSSignerFactory;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class SingleKeyJwtClaimsSigner implements JwtClaimsSigner {
    private final JWK key;

    @SneakyThrows
    private static JWK createJWK(KeyPair keyPair) {
        var pubKey = keyPair.getPublic();
        if(pubKey instanceof RSAPublicKey rsaPublicKey) {
            return new RSAKey.Builder(rsaPublicKey)
                    .privateKey(keyPair.getPrivate())
                    .keyIDFromThumbprint()
                    .build();
        }
        throw new IllegalStateException("Unknown keypair type");
    }

    public SingleKeyJwtClaimsSigner() {
        this(CryptoTestUtils.createKeyPair("RSA", 2048));
    }

    public SingleKeyJwtClaimsSigner(KeyPair keyPair) {
        this(createJWK(keyPair));
    }

    @Override
    public JWKSet getSigningKeys() {
        return new JWKSet(key);
    }

    @Override
    @SneakyThrows
    public SignedJWT sign(JWTClaimsSet jwtClaimsSet) {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), jwtClaimsSet);
        jwt.sign(new DefaultJWSSignerFactory().createJWSSigner(key));
        return jwt;
    }
}
