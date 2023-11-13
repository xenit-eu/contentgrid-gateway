package com.contentgrid.gateway.runtime.actuate;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import com.contentgrid.gateway.security.oidc.ReactiveClientRegistrationIdResolver;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.endpoint.OperationResponseBody;
import org.springframework.boot.actuate.endpoint.web.Link;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RestControllerEndpoint(id = "contentgrid")
public class ContentGridActuatorEndpoint {

    private final WebEndpointProperties endpointProperties;

    @NonNull
    private final ApplicationConfigurationRepository applicationConfigurationRepository;

    @NonNull
    private final ReactiveClientRegistrationIdResolver reactiveClientRegistrationIdResolver;

    @NonNull
    private final ReactiveClientRegistrationRepository clientRegistrationRepository;


    @ResponseBody
    @GetMapping(value = {"", "/"})
    public Map<String, Map<String, Link>> links(
            ServerWebExchange exchange) {

        var basePath = endpointProperties.getBasePath();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("applications", new Link(basePath + "/contentgrid/applications"));

        return OperationResponseBody.of(Collections.singletonMap("_links", links));
    }


    @GetMapping("/applications")
    public ApplicationsCollectionDescriptor listApplications() {
        return new ApplicationsCollectionDescriptor(
                this.applicationConfigurationRepository
                        .applicationIds()
                        .map(this::getApplicationDescriptorInternal)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet()));
    }

    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<ApplicationDescriptor> getApplication(@PathVariable ApplicationId applicationId) {

        return this.getApplicationDescriptorInternal(applicationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private Optional<ApplicationDescriptor> getApplicationDescriptorInternal(ApplicationId applicationId) {

        // does this app-id actually exist ?
        var config = this.applicationConfigurationRepository.getApplicationConfiguration(applicationId);
        if (config == null) {
            return Optional.empty();
        }

        var links = new LinkedHashMap<String, Link>();
        links.put("self", new Link("/actuator/contentgrid/applications/" + applicationId));
        links.put("config", new Link("/actuator/contentgrid/applications/" + applicationId + "/config"));
        links.put("client-registration",
                new Link("/actuator/contentgrid/applications/" + applicationId + "/client-registration"));

        var model = new ApplicationDescriptor(applicationId.getValue(), links);
        return Optional.of(model);
    }

    @GetMapping("/applications/{applicationId}/config")
    public ApplicationConfigurationDescriptor getApplicationConfig(@PathVariable ApplicationId applicationId) {
        var config = this.applicationConfigurationRepository.getApplicationConfiguration(applicationId);
        return ApplicationConfigurationDescriptor.from(config);
    }

    @GetMapping("/applications/{applicationId}/client-registration")
    public Mono<ClientRegistration> clientRegistration(@PathVariable ApplicationId applicationId) {
        return this.reactiveClientRegistrationIdResolver.resolveRegistrationId(applicationId)
                .flatMap(this.clientRegistrationRepository::findByRegistrationId);
    }

    /**
     * Descriptor of {@link ApplicationId}s
     */
    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public static final class ApplicationsCollectionDescriptor implements OperationResponseBody {

        @Getter
        private final Set<ApplicationDescriptor> applications;

    }

    /**
     * Representation of a single ContentGrid application
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public static final class ApplicationConfigurationDescriptor implements OperationResponseBody {

        private final String applicationId;
        private final String clientId;
        private final String clientSecret;
        private final String issuerUri;
        private final Set<String> domains;
        private final Set<String> corsOrigins;

        private final Map<String, Object> _links;


        static ApplicationConfigurationDescriptor from(ApplicationConfiguration config) {
            return new ApplicationConfigurationDescriptor(
                    config.getApplicationId().toString(),
                    config.getClientId(),
                    mask(config.getClientSecret()),
                    config.getIssuerUri(),
                    config.getDomains(),
                    config.getCorsOrigins(),
                    Map.of(
                            "api", config.getDomains().stream()
                                    .map(domain -> "https://" + domain)
                                    .map(Link::new)
                                    .toList(),
                            "application", new Link("/actuator/contentgrid/applications/" + config.getApplicationId()),
                            "issuer-uri", new Link(config.getIssuerUri())
                    ));
        }

        static String mask(String value) {
            int length = (value == null) ? 0 : value.length();
            return "*".repeat(length);
        }

    }

    @Value
    static class ApplicationDescriptor implements OperationResponseBody {

        String id;
        Map<String, Link> _links;

    }
}
