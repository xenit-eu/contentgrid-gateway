package com.contentgrid.gateway.cors;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Integration tests covering CSRF attack vectors against the gateway.
 *
 * <p>Each test simulates a victim with an active session (mockOidcLogin) and an attacker
 * issuing requests from a cross-site origin. Requests include the Sec-Fetch-* and Origin
 * headers that a modern browser would send for each attack type.
 *
 * <p>Tests in the "shouldBeForbidden" group document the requirement that cross-origin
 * POST attacks must be rejected with 403.
 *
 * <p>Tests in the negative groups ("isNotCsrfBlocked", "shouldSucceed") verify that
 * legitimate request patterns are not broken by CSRF protection.
 */
@Slf4j
@SpringBootTest(properties = {
        "contentgrid.gateway.cors.configurations.default.allowed-origins=https://trusted-app.example"
})
class CsrfProtectionIntegrationTest {

    @Autowired
    ApplicationContext context;

    WebTestClient http;

    static final String TARGET_URL = "http://localhost/test";
    // Simulated attacker origin — distinct from any configured trusted CORS origin
    static final String ATTACKER_ORIGIN = "http://attacker.example";

    @BeforeEach
    void setup() {
        this.http = WebTestClient
                .bindToApplicationContext(this.context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    // -------------------------------------------------------------------------
    // Group 1: Form-based attacks
    //
    // application/x-www-form-urlencoded and multipart/form-data are "simple"
    // content-types — the browser sends them cross-origin without a CORS preflight.
    // The session cookie travels along automatically. No JavaScript required.
    // -------------------------------------------------------------------------

    /**
     * Hidden HTML form submission
     *
     * <p>Browser Sec-Fetch-Mode is "no-cors" for form submissions — no preflight OPTIONS
     * is ever sent.
     */
    @ParameterizedTest
    @ValueSource(strings = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    void formPost_crossSite_shouldBeForbidden(String contentType) {
        http.mutateWith(mockOidcLogin()) // cookies may be sent for same-site requests; we don't trust our whole site, only our origin
                .post().uri(TARGET_URL)
                .header(HttpHeaders.ORIGIN, ATTACKER_ORIGIN)
                .header("Referer", ATTACKER_ORIGIN + "/attack.html")
                .header("Sec-Fetch-Site", "cross-site")
                .header("Sec-Fetch-Mode", "navigate")
                .contentType(MediaType.parseMediaType(contentType))
                .exchange()
                .expectStatus().isForbidden();
    }

    // -------------------------------------------------------------------------
    // Group 2: JavaScript fetch() attacks
    //
    // fetch() with credentials:include sends the session cookie. The Sec-Fetch-Mode
    // is "cors" (fetch default), which differs from HTML form mode. CORS does enforce
    // a preflight for non-simple content-types, but text/plain bypasses that.
    // -------------------------------------------------------------------------

    /**
     * fetch() POST with credentials:include and form-encoded body.
     *
     * <p>application/x-www-form-urlencoded is a simple content-type, so no CORS
     * preflight is sent even when mode is "cors". The session cookie is included.
     */
    @Test
    void fetchPost_formEncoded_withCredentials_crossSite_shouldBeForbidden() {
        http.mutateWith(mockOidcLogin())
                .post().uri(TARGET_URL)
                .header(HttpHeaders.ORIGIN, ATTACKER_ORIGIN)
                .header("Sec-Fetch-Site", "cross-site")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Dest", "empty")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .exchange()
                .expectStatus().isForbidden();
    }

    /**
     * text/plain JSON smuggling; no CORS preflight triggered.
     *
     * <p>text/plain is a safelisted content-type, so the browser sends the request
     * without a preflight OPTIONS. CORS cannot intercept it. If the backend parses
     * the body as JSON regardless of Content-Type, this is a working attack.
     */
    @Test
    void fetchPost_textPlain_jsonSmuggling_crossSite_shouldBeForbidden() {
        http.mutateWith(mockOidcLogin())
                .post().uri(TARGET_URL)
                .header(HttpHeaders.ORIGIN, ATTACKER_ORIGIN)
                .header("Sec-Fetch-Site", "cross-site")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Dest", "empty")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    /**
     * fetch() with application/json triggers a CORS preflight.
     *
     * <p>application/json is not a simple content-type, so the browser sends an
     * OPTIONS preflight first. If CORS rejects the untrusted origin, the actual
     * POST is never sent. This test verifies CORS correctly blocks the preflight
     * for a non-configured origin.
     */
    @Test
    void fetchPost_applicationJson_crossSite_preflightRejectedByCors() {
        http.options().uri(TARGET_URL)
                .header(HttpHeaders.ORIGIN, ATTACKER_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
                .header("Sec-Fetch-Site", "cross-site")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Dest", "empty")
                .exchange()
                .expectStatus().isForbidden();
    }

    // -------------------------------------------------------------------------
    // Group 3: GET-based attacks
    //
    // img/script/link tags trigger GET requests cross-origin with session cookies
    // attached. CORS does not — and should not — block GET requests.
    // The browser's opaque response prevents reading the response, so the attack
    // value is limited to endpoints with side effects on GET. The primary
    // mitigation here is SameSite cookie configuration.
    // -------------------------------------------------------------------------

    /**
     * IMG tag triggering GET
     *
     * <p>Browsers omit the Origin header on no-cors GET requests from image tags.
     * The session cookie is sent, but the response is opaque (unreadable by the
     * attacker).
     */
    @Test
    void imgGet_crossSite_isNotCsrfBlocked() {
        http.mutateWith(mockOidcLogin())// cookies may be sent for same-site requests; we don't trust our whole site, only our origin
                .get().uri(TARGET_URL)
                // Browsers do not send Origin on no-cors GET img-tag requests
                .header("Sec-Fetch-Site", "cross-site")
                .header("Sec-Fetch-Mode", "no-cors")
                .header("Sec-Fetch-Dest", "image")
                .exchange()
                .expectStatus()
                .isOk(); // This is a GET, so it's okay if the request goes through. Same-origin policy will still block reading the contents
    }

    // -------------------------------------------------------------------------
    // Group 4: SameSite=Lax bypass via top-level navigation
    //
    // SameSite=Lax allows session cookies on top-level cross-site navigations.
    // A 307 redirect can preserve a POST body through a top-level navigation,
    // resulting in a credentialed cross-site POST.
    // -------------------------------------------------------------------------

    /**
     * 307 redirect preserving a POST through a top-level browser navigation.
     *
     * <p>Sec-Fetch-Mode: navigate and Sec-Fetch-Dest: document indicate a top-level
     * navigation. SameSite=Lax cookies are included on these requests.
     */
    @Test
    void topLevelNavigation_crossSite_postPreservedVia307Redirect_shouldBeForbidden() {
        http.mutateWith(mockOidcLogin())
                .post().uri(TARGET_URL)
                .header(HttpHeaders.ORIGIN, ATTACKER_ORIGIN)
                .header("Sec-Fetch-Site", "cross-site")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Dest", "document")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .exchange()
                .expectStatus().isForbidden();
    }

    // -------------------------------------------------------------------------
    // Group 5: Negative tests — legitimate request patterns must not break
    // -------------------------------------------------------------------------

    /**
     * Bearer token (JWT) requests are not session-based and must not be blocked.
     *
     * <p>API clients authenticate with Bearer tokens rather than session cookies, so they
     * never carry CORS headers.
     *
     * <p>No Origin header is set — API clients are not browsers and do not include Origin
     * on direct calls.
     */
    @Test
    void bearerTokenPost_isNotSubjectToCsrfProtection() {
        http.mutateWith(mockJwt())
                .post().uri(TARGET_URL)
                .exchange()
                // Must not be 403 rejection
                .expectStatus().value(status -> Assertions.assertThat(status).isNotEqualTo(403));
    }

    /**
     * A legitimate same-origin POST must succeed.
     */
    @Test
    void formPost_crossSite_trusted_shouldSucceed() {
        http.mutateWith(mockOidcLogin())
                .post().uri(TARGET_URL)
                .header(HttpHeaders.ORIGIN, "https://trusted-app.example")
                .header("Sec-Fetch-Site", "cross-site")
                .header("Sec-Fetch-Mode", "cors")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .exchange()
                // Must not be rejected with 403
                .expectStatus().value(status -> Assertions.assertThat(status).isNotEqualTo(403));
    }

    @Controller
    public static class TestController {
        @RequestMapping("/test")
        ResponseEntity<String> test() {
            return ResponseEntity.ok("test");
        }
    }

    @TestConfiguration
    @Import(TestController.class)
    static class TestConfig {
    }
}
