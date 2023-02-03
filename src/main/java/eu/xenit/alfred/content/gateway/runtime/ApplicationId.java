package eu.xenit.alfred.content.gateway.runtime;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

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

    public static Optional<ApplicationId> from(@NonNull String value) {
        if (value.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new ApplicationId(value));
    }
}
