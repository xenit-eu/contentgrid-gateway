package com.contentgrid.gateway.security.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.gateway.security.authority.Actor.ActorType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.jwt.JoseHeaderNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

class ActorConverterTest {
    private static final String ISSUER = "https://issuer.invalid/r/xyz";

    @Test
    void convertsActorFromSimpleJwt() {
        var converter = new ActorConverter(ISSUER::equals, ActorType.USER, ca -> ca);

        var jwt = Jwt.withTokenValue("XYZ")
                .header(JoseHeaderNames.ALG, "none")
                .issuer(ISSUER)
                .subject("user123")
                .claim("my-claim", "vvvv")
                .build();

        var actor = converter.convert(jwt);

        assertThat(actor.getType()).isEqualTo(ActorType.USER);
        assertThat(actor.getClaims().getClaims()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "iss", ISSUER,
                "sub", "user123",
                "my-claim", "vvvv"
        ));
        assertThat(actor.getParent()).isNull();

    }

    @Test
    void doesNotConvert_nonMatchingIssuer() {
        var converter = new ActorConverter(String::isEmpty, ActorType.USER, ca -> ca);

        var jwt = Jwt.withTokenValue("XYZ")
                .header(JoseHeaderNames.ALG, "none")
                .issuer(ISSUER)
                .subject("user123")
                .claim("my-claim", "vvvv")
                .build();

        var actor = converter.convert(jwt);
        assertThat(actor).isNull();

    }

    @Test
    void rejects_missingIssuer() {
        var converter = new ActorConverter(String::isEmpty, ActorType.USER, ca -> ca);

        var jwt = Jwt.withTokenValue("XYZ")
                .header(JoseHeaderNames.ALG, "none")
                .subject("user123")
                .claim("my-claim", "vvvv")
                .build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void converts_withNestedActor() {
        var converter = new ActorConverter(ISSUER::equals, ActorType.USER, ca -> ca);

        var parentConverter = Mockito.mock(ActorConverter.class);

        converter.setParentActorConverter(parentConverter);

        var parentActor = new Actor(ActorType.EXTENSION, Map::of, null);
        Mockito.when(parentConverter.convert(Mockito.any())).thenReturn(parentActor);

        var jwt = Jwt.withTokenValue("XYZ")
                .header(JoseHeaderNames.ALG, "none")
                .issuer(ISSUER)
                .subject("user123")
                .claim("my-claim", "vvvv")
                .claim("act", Map.of(
                        "iss", ISSUER,
                        "sub", "my-actor"
                ))
                .build();

        assertThat(converter.convert(jwt)).satisfies(actor -> {
            assertThat(actor.getType()).isEqualTo(ActorType.USER);
            assertThat(actor.getClaims().getClaimAsString(JwtClaimNames.SUB)).isEqualTo("user123");
            assertThat(actor.getParent()).isSameAs(parentActor);
        });
    }
}