package com.contentgrid.gateway.security.jwt.issuer.encrypt;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@RequiredArgsConstructor
class MultipleDecryptorsTextEncryptor implements TextEncryptor {

    private final TextEncryptor encryptor;
    private final List<TextEncryptor> decryptors;

    @Override
    public String encrypt(String text) {
        return encryptor.encrypt(text);
    }

    @Override
    public String decrypt(String encryptedText) {
        RuntimeException exception = new IllegalStateException("Failed to decrypt: no decryptors");
        for (TextEncryptor decryptor : decryptors) {
            try {
                return decryptor.decrypt(encryptedText);
            } catch (IllegalStateException e) {
                exception = e;
                // just try the next decryptor
            }
        }
        throw exception;
    }
}
