package com.contentgrid.gateway.test.security;

import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.AuthenticationDetails;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class TestAuthenticationDetails implements AuthenticationDetails {
    Actor principal;
    Actor actor;
}
