package com.contentgrid.gateway.security.authority;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class DelegatedAuthenticationDetailsGrantedAuthority extends
        AbstractAuthenticationDetailsGrantedAuthority {

    private final Actor actor;

    public DelegatedAuthenticationDetailsGrantedAuthority(@NonNull Actor principal, @NonNull Actor actor) {
        super(principal);
        this.actor = actor;
    }
}
