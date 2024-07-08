package com.contentgrid.gateway.test.controller;

import com.contentgrid.gateway.security.authority.AuthenticationDetails;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@ResponseBody
@RequestMapping("/_test")
public class TestController {

    @GetMapping("authenticationDetails")
    AuthenticationDetails authenticationDetails(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .filter(AuthenticationDetails.class::isInstance)
                .map(AuthenticationDetails.class::cast)
                .findFirst()
                .orElse(null);
    }

}
