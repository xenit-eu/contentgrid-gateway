package com.contentgrid.gateway.test.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.oauth2.core.ClaimAccessor;

@JsonDeserialize(as = ClaimAccessorMixin.class)
@Getter
public class ClaimAccessorMixin implements ClaimAccessor {

    @JsonCreator
    public ClaimAccessorMixin(@JsonProperty("claims") Map<String, Object> claims) {
        this.claims = claims;
    }
    private final Map<String, Object> claims;
}
