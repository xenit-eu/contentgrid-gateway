package eu.xenit.alfred.content.gateway;

import eu.xenit.contentcloud.thunx.spring.security.AuthenticationContextMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Component;

@Component
class UserProfileService {

    private static final String[] CLAIM_BLACKLIST = new String[]{
            "acr",
            "aud",
            "at_hash",
            "azp",
            "iss",
            "jti",
            "nonce",
            "preferred_username",
            "session_state",
            "typ"
    };

    public Map<String, Object> userInfo(@AuthenticationPrincipal Authentication authentication) {

        var authContext = AuthenticationContextMapper.fromAuthentication(authentication);

        Map<String, Object> result = new HashMap<>();
        result.put("name", authentication.getName());

        result.putAll(authContext.getUser());

        // filter out stuff that is of no concern of the user
        Arrays.asList(CLAIM_BLACKLIST).forEach(result::remove);

        return result;
    }
}
