package com.contentgrid.gateway.security.authority;

import lombok.NonNull;

public class PrincipalAuthenticationDetailsGrantedAuthority extends AbstractAuthenticationDetailsGrantedAuthority {

    public PrincipalAuthenticationDetailsGrantedAuthority(@NonNull Actor principal) {
        super(principal);
    }

    @Override
    public Actor getActor() {
        return null;
    }
}
