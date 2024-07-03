package com.contentgrid.gateway.security.authority;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;

@Getter
abstract class AbstractAuthenticationDetailsGrantedAuthority implements AuthenticationDetails, GrantedAuthority {

    @NonNull
    private final Actor principal;

    protected AbstractAuthenticationDetailsGrantedAuthority(@NonNull Actor principal) {
        if(principal.getParent() != null) {
            throw new IllegalArgumentException("Principal actor can not have a parent");
        }
        this.principal = principal;
    }

    @Override
    public String getAuthority() {
        return "AUTHENTICATION_ATTRIBUTES";
    }
}
