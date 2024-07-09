package com.contentgrid.gateway;

import com.contentgrid.gateway.runtime.authorization.AuthenticationModel;
import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.AuthenticationDetails;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty("contentgrid.gateway.user-info.enabled")
public class UserInfoController {

    @GetMapping(value = "${contentgrid.gateway.user-info.path}", produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthenticationModel userInfo(Authentication authentication) {
        return AuthenticationModel.from(authentication);
    }

}
