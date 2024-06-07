package com.contentgrid.gateway.security.jwt.issuer.encrypt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.Encryptors;

class ResultCheckedBytesEncryptorTest {
    @Test
    void encryptDecryptWithSameVerification() {
        var encryptor = new ResultCheckedBytesEncryptor(Encryptors.standard("test", "abcdef"), new byte[] {1,2,3});

        var encrypted = encryptor.encrypt(new byte[] { 8,8,6 });

        assertThat(encryptor.decrypt(encrypted)).containsExactly(8,8,6);
    }

    @Test
    void encryptDecryptWithDifferentKey() {
        var encryptor = new ResultCheckedBytesEncryptor(Encryptors.standard("test", "abcdef"), new byte[] {1,2,3});
        var decryptor = new ResultCheckedBytesEncryptor(Encryptors.standard("test2", "abcdef"), new byte[] {1,2,3});

        var encrypted = encryptor.encrypt(new byte[] { 8,8,6 });

        assertThatThrownBy(() -> decryptor.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encryptDecryptWithDifferentVerification() {
        var wrapped = Encryptors.standard("test", "abcdef");
        var encryptor = new ResultCheckedBytesEncryptor(wrapped, new byte[] {1,2,3});
        var decryptor = new ResultCheckedBytesEncryptor(wrapped, new byte[] {1,2,4});

        var encrypted = encryptor.encrypt(new byte[] { 8,8,6 });

        assertThatThrownBy(() -> decryptor.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }
}