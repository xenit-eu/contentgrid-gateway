package com.contentgrid.gateway.security.jwt.issuer;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.springframework.core.io.Resource;
import org.springframework.security.util.InMemoryResource;

@UtilityClass
class CryptoTestUtils {

    @SneakyThrows
    static KeyPair createKeyPair(String algorithm, int size) {
        var generator = KeyPairGenerator.getInstance(algorithm);
        generator.initialize(size);
        return generator.generateKeyPair();
    }

    @SneakyThrows
    static Resource toKeyResource(List<PemObject> objects) {

        var privateKeyOutput = new ByteArrayOutputStream();
        try (var writer = new OutputStreamWriter(privateKeyOutput)) {
            try (var pemWriter = new PemWriter(writer)) {
                for (PemObject object : objects) {
                    pemWriter.writeObject(object);
                }
            }
        }
        return new InMemoryResource(privateKeyOutput.toByteArray());

    }

    static Resource toPrivateKeyResource(KeyPair keyPair) {
        return toKeyResource(List.of(
                new PemObject("PRIVATE KEY", keyPair.getPrivate().getEncoded()),
                new PemObject("PUBLIC KEY", keyPair.getPublic().getEncoded()))
        );
    }

    static Resource toPublicKeyResource(KeyPair keyPair) {
        return toKeyResource(List.of(
                new PemObject("PUBLIC KEY", keyPair.getPublic().getEncoded())
        ));
    }
}
