package com.contentgrid.gateway.security.jwt.issuer;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class PlainOAuth2UserDetailsAuthenticationInformationResolver extends
        AbstractOAuth2UserDetailsAuthenticationInformationResolver<OAuth2UserRequest> {

    public PlainOAuth2UserDetailsAuthenticationInformationResolver(
            ReactiveOAuth2AuthorizedClientManager clientManager,
            ReactiveOAuth2UserService<OAuth2UserRequest, OAuth2User> userService) {
        super(clientManager, userService);
    }

    @Override
    protected OAuth2UserRequest createUserRequest(OAuth2AuthorizedClient authorizedClient) {
        return new OAuth2UserRequest(authorizedClient.getClientRegistration(), authorizedClient.getAccessToken());
    }
}
