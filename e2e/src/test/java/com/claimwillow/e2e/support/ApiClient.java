package com.claimwillow.e2e.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sets up scenario data through the real public API (session + CSRF), so browser steps start
 * from a known state without depending on other scenarios.
 */
public final class ApiClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;

    private ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
    }

    public static ApiClient signedIn(String baseUrl, String email, String password) {
        ApiClient client = new ApiClient(baseUrl);
        client.send("GET", "/api/v1/auth/csrf", null);
        client.send("POST", "/api/v1/auth/login", Map.of("email", email, "password", password));
        return client;
    }

    private String csrf() {
        return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue).reduce((a, b) -> b).orElse("");
    }

    public JsonNode send(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
            if (!"GET".equals(method)) {
                builder.header("X-XSRF-TOKEN", csrf());
            }
            if (body != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(method + " " + path + " -> " + response.statusCode() + ": "
                        + response.body());
            }
            return response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    public String firstActivePolicyId() {
        for (JsonNode p : send("GET", "/api/v1/policies/mine", null)) {
            if ("ACTIVE".equals(p.get("status").asText())) {
                return p.get("id").asText();
            }
        }
        throw new IllegalStateException("No active policy for this user");
    }

    /** Creates and submits a complete claim; returns the claim JSON. */
    public JsonNode submitClaim(String title, String estimate) {
        return submitClaim(title, estimate, "Synthetic end-to-end test claim: water from a burst pipe damaged a sofa "
                + "and an area rug in the living room.");
    }

    public JsonNode submitClaim(String title, String estimate, String description) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("policyId", firstActivePolicyId());
        draft.put("incidentDate", LocalDate.now().minusDays(3).toString());
        draft.put("incidentType", "DAMAGE");
        draft.put("title", title);
        draft.put("description", description);
        draft.put("estimatedLoss", estimate);
        JsonNode created = send("POST", "/api/v1/claims", draft);
        return send("POST", "/api/v1/claims/" + created.get("id").asText() + "/submit",
                Map.of("version", created.get("version").asLong()));
    }

    public JsonNode createDraft(String title) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("policyId", firstActivePolicyId());
        draft.put("title", title);
        return send("POST", "/api/v1/claims", draft);
    }

    public JsonNode claim(String id) {
        return send("GET", "/api/v1/claims/" + id, null);
    }

    public JsonNode staff(String id, String action, Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>(extra);
        body.put("version", claim(id).get("version").asLong());
        return send("POST", "/api/v1/staff/claims/" + id + "/" + action, body);
    }
}
