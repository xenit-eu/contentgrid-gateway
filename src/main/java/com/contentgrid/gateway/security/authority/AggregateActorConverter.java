package com.contentgrid.gateway.security.authority;

import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.ClaimAccessor;

@RequiredArgsConstructor
public class AggregateActorConverter implements Converter<ClaimAccessor, Actor> {

    private final List<? extends Converter<ClaimAccessor, Actor>> converters;

    @Override
    public Actor convert(ClaimAccessor source) {
        return converters
                .stream()
                .flatMap(converter -> Stream.ofNullable(converter.convert(source)))
                .findFirst()
                .orElse(null);
    }
}
