package com.contentgrid.gateway.security.jwt.issuer.encrypt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import javax.crypto.spec.SecretKeySpec;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor.CipherAlgorithm;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@RequiredArgsConstructor
public class PropertiesBasedTextEncryptorFactory implements TextEncryptorFactory {
    private final ResourcePatternResolver resourcePatternResolver;
    private final TextEncryptorProperties encryptorProperties;
    private final Random random;

    public PropertiesBasedTextEncryptorFactory(ResourcePatternResolver resourcePatternResolver, TextEncryptorProperties properties) {
        this(resourcePatternResolver, properties, new Random());
    }

    @Override
    @SneakyThrows
    public TextEncryptor newEncryptor() {

        List<TextEncryptor> activeEncryptors = readKeys(encryptorProperties.getActiveKeys())
                .map(this::createEncryptor)
                .<TextEncryptor>map(Base64TextEncryptor::new)
                .toList();
        List<TextEncryptor> allEncryptors = readKeys(encryptorProperties.getAllKeys())
                .map(this::createEncryptor)
                .map(Base64TextEncryptor::new)
                .collect(Collectors.toList());
        allEncryptors.addAll(0, activeEncryptors);

        var selectedEncryptor = activeEncryptors.get(random.nextInt(activeEncryptors.size()));

        return new MultipleDecryptorsTextEncryptor(selectedEncryptor, allEncryptors);
    }

    private Stream<byte[]> readKeys(String pathPattern) {
        if(pathPattern == null) {
            return Stream.empty();
        }
        try {
            var resources = resourcePatternResolver.getResources(pathPattern);
            var keyList = new ArrayList<byte[]>(resources.length);

            for (Resource resource : resources) {
                keyList.add(resource.getContentAsByteArray());
            }
            return keyList.stream();
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BytesEncryptor createEncryptor(byte[] secretKey) {
        CRC32 crc32 = new CRC32();
        crc32.update(secretKey);
        var checksum = ByteBuffer.allocate(Long.BYTES)
                .putLong(crc32.getValue())
                .array();

        return new ResultCheckedBytesEncryptor(new AesBytesEncryptor(new SecretKeySpec(secretKey, "AES"), null, CipherAlgorithm.CBC), checksum);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TextEncryptorProperties {
        private String activeKeys;
        private String allKeys;
    }

}
