package com.contentgrid.gateway.security.jwt.issuer.encrypt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.Encryptors;

class MultipleDecryptorsTextEncryptorTest {
    @Test
    void decryptsWithAnyAvailableKey() {
        var actualEncryptor = Encryptors.text("test2", "abcdef");
        var encryptor = new MultipleDecryptorsTextEncryptor(
                Encryptors.text("test", "abcdef"),
                List.of(
                        Encryptors.text("test", "abcdef"),
                        actualEncryptor
                )
        );

        var encrypted = actualEncryptor.encrypt("test");

        assertThat(encryptor.decrypt(encrypted)).isEqualTo("test");
    }

}