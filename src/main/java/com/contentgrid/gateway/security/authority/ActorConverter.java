package com.contentgrid.gateway.security.authority;

import com.contentgrid.gateway.runtime.security.jwt.ContentGridClaimNames;
import com.contentgrid.gateway.security.authority.Actor.ActorType;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

@RequiredArgsConstructor
@AllArgsConstructor
public class ActorConverter implements Converter<ClaimAccessor, Actor> {

    private final Predicate<String> issuerMatcher;
    private final ActorType actorType;
    private final Converter<ClaimAccessor, ClaimAccessor> claimAccessorConverter;

    @Setter
    private Converter<ClaimAccessor, Actor> parentActorConverter;

    @Override
    public Actor convert(ClaimAccessor claimAccessor) {
        var issuer = claimAccessor.getClaimAsString(JwtClaimNames.ISS);
        if (issuer == null) {
            throw new IllegalArgumentException("The 'iss' claim is required for actors");
        }
        if (!issuerMatcher.test(issuer)) {
            return null;
        }
        var parentActorClaims = claimAccessor.getClaimAsMap(ContentGridClaimNames.ACT);
        Actor parentActor;
        if (parentActorClaims != null) {
            /* The actor claim has a nested actor claim. A nested claim means a "parent" actor
               The full incoming token looks like this; the incoming ClaimAccessor is the value of the top-level 'act' key
                {
                  [...]
                  "act": {
                    "iss": "https://extensions.sandbox.contentgrid.cloud/authentication/system",
                    "sub": "auth-demo",
                    "act": {
                      "iss": "https://scheduled-jobs.sandbox.contentgrid.cloud/authentication/system",
                      "sub": "other-thing"
                    }
                  }
              }
             */
            parentActor = parentActorConverter.convert(() -> parentActorClaims);
        } else {
            parentActor = null;
        }

        return new Actor(
                actorType,
                claimAccessorConverter.convert(claimAccessor),
                parentActor
        );
    }
}
