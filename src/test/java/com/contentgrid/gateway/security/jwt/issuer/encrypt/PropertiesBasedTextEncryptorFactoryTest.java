package com.contentgrid.gateway.security.jwt.issuer.encrypt;


import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.security.jwt.issuer.MockResourcePatternResolver;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.PropertiesBasedTextEncryptorFactory.TextEncryptorProperties;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.util.InMemoryResource;

class PropertiesBasedTextEncryptorFactoryTest {

    private static final BytesKeyGenerator KEY_GENERATOR = KeyGenerators.secureRandom(16);

    static Random createDeterministicRandom() {
        // This is a random that will always start from the same seed
        return new Random(5);
    }

    @Test
    void encrypts_with_active_key() {

        var resolver = MockResourcePatternResolver.builder()
                .resource("file:/keys/active.bin", new InMemoryResource(KEY_GENERATOR.generateKey()))
                .resource("file:/keys/retired-1.bin", new InMemoryResource(KEY_GENERATOR.generateKey()))
                .build();

        var textEncryptor = new PropertiesBasedTextEncryptorFactory(
                resolver,
                new TextEncryptorProperties("file:/keys/active.bin", "file:/keys/retired-*.bin")
        );

        var encrypted = textEncryptor.newEncryptor().encrypt("test");


        var textDecryptor = new PropertiesBasedTextEncryptorFactory(
                resolver,
                new TextEncryptorProperties("file:/keys/active.bin", null)
        );

        assertThat(textDecryptor.newEncryptor().decrypt(encrypted)).isEqualTo("test");
    }

    @Test
    void rotates_multiple_active_keys() {

        var resolver = MockResourcePatternResolver.builder()
                .resource("file:/keys/active-1.bin", new InMemoryResource(KEY_GENERATOR.generateKey()))
                .resource("file:/keys/active-2.bin", new InMemoryResource(KEY_GENERATOR.generateKey()))
                .build();

        var textEncryptor = new PropertiesBasedTextEncryptorFactory(
                resolver,
                new TextEncryptorProperties("file:/keys/active-*.bin", "file:/keys/retired-*.bin"),
                createDeterministicRandom()
        );

        var textDecryptor1 = new PropertiesBasedTextEncryptorFactory(
                resolver,
                new TextEncryptorProperties("file:/keys/active-1.bin", null)
        ).newEncryptor();
        var textDecryptor2 = new PropertiesBasedTextEncryptorFactory(
                resolver,
                new TextEncryptorProperties("file:/keys/active-2.bin", null)
        ).newEncryptor();

        Set<TextEncryptor> seenEncryptors = new HashSet<>();
        // try 20 times to encrypt data, so we can collect a sample of the encryption keys
        // Encryption keys are selected "randomly" using the deterministic random above,
        // so it's not left up to chance which keys are actually selected

        for(int i = 0; i < 20; i++) {
            var encrypted = textEncryptor.newEncryptor().encrypt("test");

            try {
                assertThat(textDecryptor1.decrypt(encrypted)).isEqualTo("test");
                seenEncryptors.add(textDecryptor1);
            } catch(IllegalStateException ex) {
                // do nothing
            }

            try {
                assertThat(textDecryptor2.decrypt(encrypted)).isEqualTo("test");
                seenEncryptors.add(textDecryptor2);
            } catch(IllegalStateException ex) {
                // do nothing
            }
        }

        assertThat(seenEncryptors).hasSize(2);
    }

    @Test
    void decrypts_with_all_keys() {
        var resolver = MockResourcePatternResolver.builder()
                .resource("file:/keys/active.bin", new InMemoryResource(KEY_GENERATOR.generateKey()))
                .resource("file:/keys/retired-1.bin", new InMemoryResource(KEY_GENERATOR.generateKey()))
                .build();

        var textDecryptor = new PropertiesBasedTextEncryptorFactory(
                resolver,
                new TextEncryptorProperties("file:/keys/active.bin", "file:/keys/retired-*.bin")
        );
        var textEncryptor = new PropertiesBasedTextEncryptorFactory(
                resolver,
                new TextEncryptorProperties("file:/keys/retired-1.bin", null)
        );

        var encrypted = textEncryptor.newEncryptor().encrypt("test");

        assertThat(textDecryptor.newEncryptor().decrypt(encrypted)).isEqualTo("test");
    }

}