package com.contentgrid.gateway.security.jwt.issuer.encrypt;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;

@RequiredArgsConstructor
public class ResultCheckedBytesEncryptor implements BytesEncryptor {
    private final BytesEncryptor delegate;
    private final byte[] verificationTag;

    @Override
    public byte[] encrypt(byte[] byteArray) {
        var withVerification = new byte[byteArray.length+verificationTag.length];
        System.arraycopy(verificationTag, 0, withVerification, 0, verificationTag.length);
        System.arraycopy(byteArray, 0, withVerification, verificationTag.length, byteArray.length);
        return delegate.encrypt(withVerification);
    }

    @Override
    public byte[] decrypt(byte[] encryptedByteArray) {
        var decrypted = delegate.decrypt(encryptedByteArray);
        if(!Arrays.equals(decrypted, 0, verificationTag.length, verificationTag, 0, verificationTag.length)) {
            throw new IllegalStateException("Verification tag does not match");
        }
        return Arrays.copyOfRange(decrypted, verificationTag.length, decrypted.length);
    }
}
