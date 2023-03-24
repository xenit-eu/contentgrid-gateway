package com.contentgrid.gateway.runtime.application;

import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.util.Assert;

@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class DeploymentId {

    @NonNull
    @Getter
    private final String value;

    @Override
    public String toString() {
        return this.getValue();
    }

    public static DeploymentId from(@NonNull String value) {
        Assert.hasText(value, "'value' must not be empty");
        return new DeploymentId(value);
    }

    public static DeploymentId random() {
        return new DeploymentId(UUID.randomUUID().toString());
    }
}
