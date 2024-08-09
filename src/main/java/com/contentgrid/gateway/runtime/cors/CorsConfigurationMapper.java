package com.contentgrid.gateway.runtime.cors;

import com.contentgrid.configuration.applications.ApplicationConfiguration;
import java.util.function.Function;
import org.springframework.web.cors.CorsConfiguration;

@FunctionalInterface
public interface CorsConfigurationMapper extends Function<ApplicationConfiguration, CorsConfiguration> {

}

