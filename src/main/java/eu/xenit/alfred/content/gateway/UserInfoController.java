package eu.xenit.alfred.content.gateway;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserInfoController {

    private final UserProfileService userProfileService;

    UserInfoController(UserProfileService userProfileService) {

        this.userProfileService = userProfileService;
    }

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> userInfo(@AuthenticationPrincipal Authentication user) {
        return this.userProfileService.userInfo(user);
    }
}
