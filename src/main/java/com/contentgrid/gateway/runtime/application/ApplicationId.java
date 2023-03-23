package com.contentgrid.gateway.runtime.application;

import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationId {

    @NonNull
    @Getter
    private final String value;

    @Override
    public String toString() {
        return this.getValue();
    }

    public static ApplicationId from(@NonNull String value) {
        Assert.hasText(value, "'value' must not be empty");
        return new ApplicationId(value);
    }

    public static ApplicationId random() {
        return new ApplicationId(UUID.randomUUID().toString());
    }
}
