package com.contentgrid.gateway.security.jwt.issuer.encrypt;

import org.springframework.security.crypto.encrypt.TextEncryptor;

public interface TextEncryptorFactory {
    TextEncryptor newEncryptor();
}
