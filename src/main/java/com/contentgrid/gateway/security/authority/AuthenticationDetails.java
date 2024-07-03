package com.contentgrid.gateway.security.authority;


public interface AuthenticationDetails {

    Actor getPrincipal();

    Actor getActor();
}
