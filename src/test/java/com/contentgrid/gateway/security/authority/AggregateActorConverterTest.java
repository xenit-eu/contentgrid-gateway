package com.contentgrid.gateway.security.authority;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.security.authority.Actor.ActorType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.ClaimAccessor;

@MockitoSettings
class AggregateActorConverterTest {
    @Mock
    private Converter<ClaimAccessor, Actor> converter1;

    @Mock
    private Converter<ClaimAccessor, Actor> converter2;

    @Test
    void delegates_toFirst() {
        var converter = new AggregateActorConverter(List.of(converter1, converter2));

        var actor = new Actor(ActorType.USER, Map::of, null);

        Mockito.when(converter1.convert(Mockito.any())).thenReturn(actor);
        assertThat(converter.convert(Map::of)).isSameAs(actor);

        Mockito.verifyNoInteractions(converter2);
    }

    @Test
    void delegates_toFirstMatching() {
        var converter = new AggregateActorConverter(List.of(converter1, converter2));

        var actor = new Actor(ActorType.USER, Map::of, null);

        Mockito.when(converter1.convert(Mockito.any())).thenReturn(null);
        Mockito.when(converter2.convert(Mockito.any())).thenReturn(actor);

        assertThat(converter.convert(Map::of)).isSameAs(actor);

        Mockito.verify(converter1).convert(Mockito.any());
    }

}