package com.claimwillow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import com.claimwillow.support.IntegrationTest;
import com.claimwillow.support.TestFixtures;
import com.claimwillow.user.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Exercises the real session + cookie CSRF flow the SPA uses (no test-only CSRF shortcuts). */
@IntegrationTest
class AuthIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestFixtures fixtures;

    private Cookie fetchCsrfCookie() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN")).andReturn();
        return result.getResponse().getCookie("XSRF-TOKEN");
    }

    /** The login response clears the old token and sets a new one; browsers keep the last value. */
    static Cookie lastCookie(MvcResult result, String name) {
        Cookie found = null;
        for (Cookie c : result.getResponse().getCookies()) {
            if (c.getName().equals(name)) {
                found = c;
            }
        }
        return found;
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        AppUser user = fixtures.policyholder();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getEmail(), TestFixtures.PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginWithCsrfTokenCreatesSessionAndRotatesToken() throws Exception {
        AppUser user = fixtures.policyholder();
        Cookie csrf = fetchCsrfCookie();
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .content(loginBody(user.getEmail().toUpperCase(), TestFixtures.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("POLICYHOLDER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        Cookie rotated = lastCookie(login, "XSRF-TOKEN");
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotBlank().isNotEqualTo(csrf.getValue());

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(user.getDisplayName()));
    }

    @Test
    void stateChangingRequestFailsWithoutTokenAndSucceedsWithIt() throws Exception {
        AppUser user = fixtures.policyholder();
        Cookie csrf = fetchCsrfCookie();
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .content(loginBody(user.getEmail(), TestFixtures.PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        Cookie token = lastCookie(login, "XSRF-TOKEN");

        mvc.perform(post("/api/v1/auth/logout").session(session).cookie(token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").session(session).cookie(token)
                        .header("X-XSRF-TOKEN", "not-the-token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").session(session).cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameGenericError() throws Exception {
        AppUser user = fixtures.policyholder();
        Cookie csrf = fetchCsrfCookie();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .content(loginBody(user.getEmail(), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .content(loginBody("nobody@example.com", "whatever-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
    }

    @Test
    void malformedLoginIsBadRequest() throws Exception {
        Cookie csrf = fetchCsrfCookie();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void repeatedFailuresAreRateLimited() throws Exception {
        AppUser user = fixtures.policyholder();
        Cookie csrf = fetchCsrfCookie();
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                            .content(loginBody(user.getEmail(), "wrong-password-" + i)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .content(loginBody(user.getEmail(), TestFixtures.PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void disabledAccountCannotSignIn() throws Exception {
        AppUser user = fixtures.disabled(fixtures.policyholder());
        Cookie csrf = fetchCsrfCookie();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .content(loginBody(user.getEmail(), TestFixtures.PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
