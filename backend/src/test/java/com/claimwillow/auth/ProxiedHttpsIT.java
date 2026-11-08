package com.claimwillow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.claimwillow.support.TestFixtures;
import com.claimwillow.support.TestcontainersConfiguration;
import com.claimwillow.user.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Real HTTP against the embedded server, as a TLS-terminating proxy (for example a hosted web
 * service) would forward it: plain HTTP inside, {@code X-Forwarded-Proto: https} outside.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProxiedHttpsIT {

    @LocalServerPort
    int port;

    @Autowired
    TestFixtures fixtures;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-For", "203.0.113.10");
    }

    private static String cookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(name + "=") && !c.startsWith(name + "=;"))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No " + name + " cookie in " + response.headers()));
    }

    private static String value(String setCookie) {
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    @Test
    void cookiesAreSecureAndCsrfStillWorksBehindAnHttpsProxy() throws Exception {
        AppUser user = fixtures.policyholder();

        HttpResponse<String> csrf = http.send(request("/api/v1/auth/csrf").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(csrf.statusCode()).isEqualTo(200);
        String xsrf = cookie(csrf, "XSRF-TOKEN");
        assertThat(xsrf).contains("Secure").contains("SameSite=Lax").doesNotContain("HttpOnly");
        assertThat(csrf.headers().firstValue("Strict-Transport-Security")).isPresent();

        String body = "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + TestFixtures.PASSWORD + "\"}";
        HttpResponse<String> noToken = http.send(request("/api/v1/auth/login")
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + value(xsrf))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(noToken.statusCode()).isEqualTo(403);

        HttpResponse<String> login = http.send(request("/api/v1/auth/login")
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + value(xsrf))
                .header("X-XSRF-TOKEN", value(xsrf))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        String session = cookie(login, "CLAIMWILLOW_SESSION");
        assertThat(session).contains("Secure").contains("HttpOnly").contains("SameSite=Lax");

        HttpResponse<String> me = http.send(request("/api/v1/auth/me")
                .header("Cookie", String.join("; ", List.of("CLAIMWILLOW_SESSION=" + value(session))))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(user.getEmail());
    }
}
