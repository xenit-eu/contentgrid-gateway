package com.contentgrid.gateway.security.jwt.issuer;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public abstract class AbstractOAuth2UserDetailsAuthenticationInformationResolver<R extends OAuth2UserRequest>
        implements AuthenticationInformationResolver {

    private final ReactiveOAuth2AuthorizedClientManager clientManager;
    private final ReactiveOAuth2UserService<R, ? extends OAuth2User> userService;

    protected Mono<ClaimAccessor> loadClaimAccessor(OAuth2AuthorizedClient authorizedClient) {
        return userService.loadUser(createUserRequest(authorizedClient))
                .map(userDetails -> userDetails::getAttributes);
    }

    protected abstract R createUserRequest(OAuth2AuthorizedClient authorizedClient);

    protected Mono<ClaimAccessor> createUpdatedClaimsAccessor(OAuth2AuthenticationToken oauth2Authentication,
            OAuth2AuthorizedClient authorizedClient) {
        return Mono.defer(() -> loadClaimAccessor(authorizedClient)
                .switchIfEmpty(Mono.just(createStaleClaimsAccessor(oauth2Authentication))));
    }

    @Override
    public Mono<AuthenticationInformation> resolve(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken oauth2Authentication) {
            var authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(
                            oauth2Authentication.getAuthorizedClientRegistrationId())
                    .principal(oauth2Authentication)
                    .build();

            return clientManager.authorize(authorizeRequest)
                    .flatMap(authorizedClient -> createAuthenticationInformation(oauth2Authentication, authorizedClient));
        }

        return Mono.empty();
    }

    protected Mono<AuthenticationInformation> createAuthenticationInformation(OAuth2AuthenticationToken oauth2Authentication,
            OAuth2AuthorizedClient authorizedClient) {
        return Mono.just(AuthenticationInformation.builder()
                .issuer(authorizedClient.getClientRegistration().getProviderDetails().getIssuerUri())
                .subject(authorizedClient.getPrincipalName())
                .expiration(Optional.ofNullable(authorizedClient.getAccessToken().getExpiresAt()).orElse(Instant.MAX))
                .staleClaims(createStaleClaimsAccessor(oauth2Authentication))
                .updatedClaims(createUpdatedClaimsAccessor(oauth2Authentication, authorizedClient))
                .build());
    }

    protected ClaimAccessor createStaleClaimsAccessor(OAuth2AuthenticationToken oAuth2Authentication) {
        return oAuth2Authentication.getPrincipal()::getAttributes;
    }
}
