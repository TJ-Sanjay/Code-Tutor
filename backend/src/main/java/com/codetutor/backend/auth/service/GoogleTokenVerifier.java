package com.codetutor.backend.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Checks a "Sign in with Google" ID token by asking Google to validate it (tokeninfo endpoint), then makes sure the token
 * was issued for THIS application (audience = our client id), that Google has verified the e-mail address and that the
 * token has not expired. Nothing is trusted from the browser except the signed token itself.
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    public record Profile(String email, String name, String picture) {}

    private final String clientId;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public GoogleTokenVerifier(@Value("${google.client-id:}") String clientId, ObjectMapper mapper) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.mapper = mapper;
    }

    public boolean enabled() { return !clientId.isBlank(); }

    public String clientId() { return clientId; }

    public Optional<Profile> verify(String idToken) {
        if (!enabled() || idToken == null || idToken.isBlank() || idToken.length() > 4096) return Optional.empty();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token="
                    + URLEncoder.encode(idToken, StandardCharsets.UTF_8))).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Google rejected the sign-in token (HTTP {})", res.statusCode());
                return Optional.empty();
            }
            JsonNode n = mapper.readTree(res.body());
            if (!clientId.equals(n.path("aud").asText())) {
                log.warn("Google token was issued for a different application");
                return Optional.empty();
            }
            if (!Set.of("accounts.google.com", "https://accounts.google.com").contains(n.path("iss").asText())) return Optional.empty();
            if (!"true".equals(n.path("email_verified").asText())) return Optional.empty();
            if (n.path("exp").asLong(0) * 1000 < System.currentTimeMillis()) return Optional.empty();
            String email = n.path("email").asText("");
            if (email.isBlank()) return Optional.empty();
            return Optional.of(new Profile(email.toLowerCase(), n.path("name").asText(""), n.path("picture").asText("")));
        } catch (Exception e) {
            log.warn("Could not verify the Google sign-in token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
