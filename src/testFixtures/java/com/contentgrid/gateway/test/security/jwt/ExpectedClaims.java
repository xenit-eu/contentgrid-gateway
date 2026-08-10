package com.contentgrid.gateway.test.security.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ExpectedClaims {

    public static Map<String, Object> fromResource(String resource) {
        return fromResource(resource, Map.of());
    }

    public static Map<String, Object> fromResource(String resource, Map<String, String> placeholders) {
        try (var stream = ExpectedClaims.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("Test resource not found: " + resource);
            }
            var json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (var placeholder : placeholders.entrySet()) {
                json = json.replace("${" + placeholder.getKey() + "}", placeholder.getValue());
            }
            return JWTClaimsSet.parse(json).toJSONObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid claims JSON in " + resource, e);
        }
    }
}
