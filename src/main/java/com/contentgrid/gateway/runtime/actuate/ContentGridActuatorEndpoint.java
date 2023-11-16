package com.contentgrid.gateway.runtime.actuate;

import com.contentgrid.gateway.runtime.actuate.ContentGridActuatorEndpoint.ApplicationsCollectionDescriptor.ApplicationConfigurationDescriptor;
import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import com.contentgrid.gateway.security.oidc.ReactiveClientRegistrationIdResolver;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistration.ProviderDetails;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
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
    public Map<String, Map<String, Link>> links() {
        var basePath = endpointProperties.getBasePath();
        var links = Map.of("applications", new Link(basePath + "/contentgrid/applications"));

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
    public Mono<ClientRegistrationDescriptor> clientRegistration(@PathVariable ApplicationId applicationId) {
        return this.reactiveClientRegistrationIdResolver.resolveRegistrationId(applicationId)
                .flatMap(registrationId -> this.clientRegistrationRepository
                        .findByRegistrationId(registrationId)
                        .map(ClientRegistrationModel::from)
                        .map(ClientRegistrationDescriptor::success)
                        .onErrorResume(throwable -> Mono.just(ClientRegistrationDescriptor.failed(registrationId, throwable)))
                )
                .map(descriptor -> {
                    descriptor.addLink("self", "/actuator/contentgrid/applications/%s/client-registration".formatted(applicationId));
                    descriptor.addLink("application", "/actuator/contentgrid/applications/%s".formatted(applicationId));

                    return descriptor;
                });

    }

    /**
     * Descriptor of {@link ApplicationId}s
     */
    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public static final class ApplicationsCollectionDescriptor implements OperationResponseBody {

        @Getter
        private final Set<ApplicationDescriptor> applications;


        /**
         * Representation of a single ContentGrid application
         */
        @Getter
        @JsonInclude
        @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
        public static final class ApplicationConfigurationDescriptor implements OperationResponseBody {

            private final String applicationId;
            private final String clientId;
            private final String clientSecret;
            private final String issuerUri;
            private final Set<String> domains;
            private final Set<String> corsOrigins;

            @JsonProperty("_links")
            private final Map<String, Object> links = new LinkedHashMap<>();

            static ApplicationConfigurationDescriptor from(ApplicationConfiguration config) {
                return new ApplicationConfigurationDescriptor(
                        config.getApplicationId().toString(),
                        config.getClientId(),
                        mask(config.getClientSecret()),
                        config.getIssuerUri(),
                        config.getDomains(),
                        config.getCorsOrigins())
                        .addLinks("api", config.getDomains().stream()
                                .map(domain -> "https://" + domain)
                                .map(Link::new)
                                .toList())
                        .addLink("application", "/actuator/contentgrid/applications/" + config.getApplicationId())
                        .addLinkIf(config.getIssuerUri() != null, "issuer-uri", config.getIssuerUri());
            }

            public ApplicationConfigurationDescriptor addLink(String name, String href) {
                return this.addLink(name, new Link(href));
            }
            public ApplicationConfigurationDescriptor addLink(String name, Link link) {
                this.links.put(name, link);
                return this;
            }

            public ApplicationConfigurationDescriptor addLinks(String name, List<Link> links) {
                this.links.put(name, links);
                return this;
            }

            public ApplicationConfigurationDescriptor addLinkIf(boolean guard, String name, String href) {
                if (guard) {
                    this.addLink(name, href);
                }
                return this;
            }

            static String mask(String value) {
                int length = (value == null) ? 0 : value.length();
                return "*".repeat(length);
            }

        }
    }

    @Value
    static class ApplicationDescriptor implements OperationResponseBody {

        String id;
        Map<String, Link> _links;

    }

    @RequiredArgsConstructor
    @JsonInclude(Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    static class ClientRegistrationDescriptor implements OperationResponseBody {

        @Getter
        @NonNull
        private final String registrationId;

        @Getter
        @Nullable
        private final ClientRegistrationModel clientRegistration;

        @Getter
        @Nullable
        private final ThrowableModel error;

        @Getter
        @JsonProperty("_links")
        private final Map<String, Link> links = new LinkedHashMap<>();

        static ClientRegistrationDescriptor success(@NonNull ClientRegistrationModel model) {
            return new ClientRegistrationDescriptor(model.registrationId(), model, null);
        }

        static ClientRegistrationDescriptor failed(@NonNull String registrationId, @NonNull Throwable throwable) {
            return new ClientRegistrationDescriptor(registrationId, null, ThrowableModel.from(throwable));
        }

        public boolean isSuccess() {
            return this.clientRegistration != null;
        }

        public ClientRegistrationDescriptor addLink(String name, String href) {
            return this.addLink(name, new Link(href));
        }
        public ClientRegistrationDescriptor addLink(String name, Link link) {
            this.links.put(name, link);
            return this;
        }
    }

    record ClientRegistrationModel(
            String registrationId,
            String clientId,
            String clientSecret,
            ClientAuthenticationMethod clientAuthenticationMethod,
            AuthorizationGrantType authorizationGrantType,
            String redirectUri,
            Set<String> scopes,
            ProviderDetails providerDetails,
            String clientName
    ) {

        static ClientRegistrationModel from(ClientRegistration client) {
            return new ClientRegistrationModel(
                    client.getRegistrationId(),
                    client.getClientId(),
                    "*".repeat(client.getClientSecret() == null ? 0 : client.getClientSecret().length()),
                    client.getClientAuthenticationMethod(),
                    client.getAuthorizationGrantType(),
                    client.getRedirectUri(),
                    client.getScopes(),
                    client.getProviderDetails(),
                    client.getClientName()
            );
        }
    }

    record ThrowableModel(@NonNull String message, @NonNull List<String> causes) {

        static ThrowableModel from(@NonNull Throwable throwable) {
            var message = throwable.getMessage();
            var causes = Stream.iterate(throwable, t -> t.getCause() != null, Throwable::getCause)
                    .map(Throwable::getMessage)
                    .filter(msg -> msg != null && !msg.isEmpty())
                    .toList();

            return new ThrowableModel(message, causes);
        }
    }
}
