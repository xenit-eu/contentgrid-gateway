package com.contentgrid.gateway.security.jwt.issuer;

import java.util.Map;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class OIDCUserDetailsAuthenticationInformationResolver extends
        AbstractOAuth2UserDetailsAuthenticationInformationResolver<OidcUserRequest> {

    public OIDCUserDetailsAuthenticationInformationResolver(ReactiveOAuth2AuthorizedClientManager clientManager,
            ReactiveOAuth2UserService<OidcUserRequest, OidcUser> userService) {
        super(clientManager, userService);
    }

    @Override
    protected OidcUserRequest createUserRequest(OAuth2AuthorizedClient authorizedClient) {
        var fakeIdToken = new OidcIdToken("fake", authorizedClient.getAccessToken().getIssuedAt(), authorizedClient.getAccessToken()
                .getExpiresAt(), Map.of());
        return new OidcUserRequest(authorizedClient.getClientRegistration(), authorizedClient.getAccessToken(), fakeIdToken);
    }
}
