package com.contentgrid.gateway.runtime.config.kubernetes;

import com.contentgrid.gateway.runtime.config.ApplicationConfigurationFragment;
import java.util.Optional;
import java.util.function.Function;

@FunctionalInterface
public interface ApplicationConfigurationMapper<T> extends Function<T, Optional<ApplicationConfigurationFragment>> {

}
