package com.contentgrid.gateway.security.jwt.issuer.encrypt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.BytesEncryptor;

class Base64TextEncryptorTest {

    private static class NoOpBytesEncryptor implements BytesEncryptor {

        @Override
        public byte[] encrypt(byte[] byteArray) {
            return byteArray;
        }

        @Override
        public byte[] decrypt(byte[] encryptedByteArray) {
            return encryptedByteArray;
        }
    }

    @Test
    void encodesBytesToBase64WhenEncrypting() {
        var encryptor = new Base64TextEncryptor(new NoOpBytesEncryptor());
        var testText = "testText";

        assertThat(encryptor.encrypt(testText)).isEqualTo("dGVzdFRleHQ=");
    }

    @Test
    void decodesBytesFromBase64WhenDecrypting() {
        var encryptor = new Base64TextEncryptor(new NoOpBytesEncryptor());
        var testText = "dGVzdFRleHQ=";

        assertThat(encryptor.decrypt(testText)).isEqualTo("testText");
    }

}