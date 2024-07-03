package com.contentgrid.gateway.test.security;

import com.contentgrid.gateway.security.jwt.issuer.encrypt.Base64TextEncryptor;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.TextEncryptorFactory;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

public class FakeBase64TextEncryptorFactory implements TextEncryptorFactory {

    @Override
    public TextEncryptor newEncryptor() {
        return new Base64TextEncryptor(new FakePassthroughBytesEncryptor());
    }

    private static class FakePassthroughBytesEncryptor implements BytesEncryptor {

        @Override
        public byte[] encrypt(byte[] byteArray) {
            return byteArray;
        }

        @Override
        public byte[] decrypt(byte[] encryptedByteArray) {
            return encryptedByteArray;
        }
    }
}
