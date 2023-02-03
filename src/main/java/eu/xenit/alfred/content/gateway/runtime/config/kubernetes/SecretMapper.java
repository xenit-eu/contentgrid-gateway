package eu.xenit.alfred.content.gateway.runtime.config.kubernetes;

import eu.xenit.alfred.content.gateway.runtime.config.ApplicationConfigurationFragment;
import java.util.Optional;
import java.util.function.Function;

@FunctionalInterface
public interface SecretMapper<T> extends Function<T, Optional<ApplicationConfigurationFragment>> {

}
