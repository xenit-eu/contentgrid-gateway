package com.contentgrid.gateway.runtime.authorization;

import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.authority.AuthenticationDetails;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

@Value
@Builder
public class AuthenticationModel {

    AuthenticationKind kind;

    PrincipalModel principal;

    ActorModel actor;

    public enum AuthenticationKind {
        @JsonProperty("anonymous")
        ANONYMOUS,
        @JsonProperty("user")
        USER,
        @JsonProperty("delegated")
        DELEGATED,
        @JsonProperty("system")
        SYSTEM
    }

    @JsonProperty("authenticated")
    public boolean isAuthenticated() {
        return kind != AuthenticationKind.ANONYMOUS;
    }

    @Value
    public static class ActorModel {

        ActorKind kind;
        String sub;
    }

    @Value
    public static class PrincipalModel {

        ActorKind kind;
        @JsonAnyGetter
        Map<String, Object> claims;

    }

    @RequiredArgsConstructor
    public enum ActorKind {
        @JsonProperty("user")
        USER(ActorType.USER),
        @JsonProperty("extension")
        EXTENSION(ActorType.EXTENSION);
        private final ActorType actorType;

        public static ActorKind fromType(ActorType actorType) {
            for (ActorKind actorKind : values()) {
                if (actorKind.actorType == actorType) {
                    return actorKind;
                }
            }
            throw new IllegalArgumentException("ActorType '%s' is not mapped to any ActorKind.".formatted(actorType));
        }
    }

    public static AuthenticationModel from(Authentication authenticationContext) {
        var maybeAuthenticationDetails = authenticationContext.getAuthorities()
                .stream()
                .filter(AuthenticationDetails.class::isInstance)
                .map(AuthenticationDetails.class::cast)
                .findFirst();
        if (maybeAuthenticationDetails.isEmpty()) {
            return AuthenticationModel.builder()
                    .kind(AuthenticationKind.ANONYMOUS)
                    .build();
        }

        var authenticationDetails = maybeAuthenticationDetails.get();

        return AuthenticationModel.builder()
                .kind(createAuthenticationKind(authenticationDetails))
                .principal(createPrincipal(authenticationDetails))
                .actor(createActor(authenticationDetails))
                .build();
    }

    private static AuthenticationKind createAuthenticationKind(AuthenticationDetails authenticationDetails) {
        if (authenticationDetails.getActor() != null) {
            return AuthenticationKind.DELEGATED;
        }
        return switch (authenticationDetails.getPrincipal().getType()) {
            case USER -> AuthenticationKind.USER;
            case EXTENSION -> AuthenticationKind.SYSTEM;
        };
    }

    private static PrincipalModel createPrincipal(AuthenticationDetails authenticationDetails) {
        return new PrincipalModel(
                ActorKind.fromType(authenticationDetails.getPrincipal().getType()),
                authenticationDetails.getPrincipal().getClaims().getClaims()
        );
    }

    private static ActorModel createActor(AuthenticationDetails authenticationDetails) {
        var actor = authenticationDetails.getActor();
        if (actor == null) {
            return null;
        }
        return new ActorModel(
                ActorKind.fromType(actor.getType()),
                actor.getClaims().getClaimAsString(JwtClaimNames.SUB)
        );
    }

}
