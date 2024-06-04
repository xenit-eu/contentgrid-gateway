package com.contentgrid.gateway.security.jwt.issuer.encrypt;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@RequiredArgsConstructor
public class Base64TextEncryptor implements TextEncryptor {
    private final BytesEncryptor bytesEncryptor;
    private final Charset charset;

    public Base64TextEncryptor(BytesEncryptor bytesEncryptor) {
        this(bytesEncryptor, StandardCharsets.UTF_8);
    }

    private static final Base64.Encoder base64Encoder = Base64.getEncoder();
    private static final Base64.Decoder base64Decoder = Base64.getDecoder();

    @Override
    public String encrypt(String text) {
        return base64Encoder.encodeToString(bytesEncryptor.encrypt(text.getBytes(charset)));
    }

    @Override
    public String decrypt(String encryptedText) {
        return new String(bytesEncryptor.decrypt(base64Decoder.decode(encryptedText)), charset);
    }
}
