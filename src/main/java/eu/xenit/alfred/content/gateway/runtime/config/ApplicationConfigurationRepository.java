package eu.xenit.alfred.content.gateway.runtime.config;

import eu.xenit.alfred.content.gateway.collections.ObservableMap.MapUpdate;
import eu.xenit.alfred.content.gateway.runtime.ApplicationId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ApplicationConfigurationRepository {

    Mono<ApplicationConfiguration> getApplicationConfiguration(ApplicationId appId);

    Flux<MapUpdate<ApplicationId, ApplicationConfiguration>> observe();

}
