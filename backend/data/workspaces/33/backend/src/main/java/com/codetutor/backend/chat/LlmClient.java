package com.codetutor.backend.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Optional large-language-model backend (Anthropic, OpenAI or Gemini). Configure with LLM_PROVIDER / LLM_API_KEY.
 * Without a key every call returns empty and the chat falls back to the grounded, graph-based answer.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    public record Turn(String role, String content) {}

    private final String provider;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public LlmClient(@Value("${llm.provider:none}") String provider, @Value("${llm.api-key:}") String apiKey,
                     @Value("${llm.model:}") String model, @Value("${llm.base-url:}") String baseUrl, ObjectMapper mapper) {
        this.provider = provider == null ? "none" : provider.trim().toLowerCase();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.mapper = mapper;
    }

    public boolean enabled() { return !provider.equals("none") && !apiKey.isBlank(); }

    public String provider() { return enabled() ? provider : "none"; }

    public Optional<String> complete(String system, List<Turn> turns) {
        if (!enabled()) return Optional.empty();
        try {
            return switch (provider) {
                case "anthropic" -> Optional.ofNullable(anthropic(system, turns));
                case "openai" -> Optional.ofNullable(openai(system, turns));
                case "gemini" -> Optional.ofNullable(gemini(system, turns));
                default -> Optional.empty();
            };
        } catch (Exception e) {
            log.warn("LLM call failed ({}): {}", provider, e.getMessage());
            return Optional.empty();
        }
    }

    private String anthropic(String system, List<Turn> turns) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model.isBlank() ? "claude-sonnet-5-5" : model);
        body.put("max_tokens", 1500);
        body.put("system", system);
        ArrayNode msgs = body.putArray("messages");
        for (Turn t : turns) msgs.addObject().put("role", t.role()).put("content", t.content());
        HttpRequest req = HttpRequest.newBuilder(URI.create((baseUrl.isBlank() ? "https://api.anthropic.com" : baseUrl) + "/v1/messages"))
                .timeout(Duration.ofSeconds(60)).header("content-type", "application/json").header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        JsonNode n = send(req);
        JsonNode c = n.path("content");
        StringBuilder sb = new StringBuilder();
        if (c.isArray()) for (JsonNode part : c) sb.append(part.path("text").asText(""));
        return sb.length() == 0 ? null : sb.toString();
    }

    private String openai(String system, List<Turn> turns) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model.isBlank() ? "gpt-4o-mini" : model);
        body.put("temperature", 0.2);
        ArrayNode msgs = body.putArray("messages");
        msgs.addObject().put("role", "system").put("content", system);
        for (Turn t : turns) msgs.addObject().put("role", t.role()).put("content", t.content());
        HttpRequest req = HttpRequest.newBuilder(URI.create((baseUrl.isBlank() ? "https://api.openai.com" : baseUrl) + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(60)).header("content-type", "application/json").header("authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        JsonNode n = send(req);
        String t = n.path("choices").path(0).path("message").path("content").asText("");
        return t.isBlank() ? null : t;
    }

    private String gemini(String system, List<Turn> turns) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        ArrayNode contents = body.putArray("contents");
        for (Turn t : turns) {
            ObjectNode c = contents.addObject();
            c.put("role", t.role().equals("assistant") ? "model" : "user");
            c.putArray("parts").addObject().put("text", t.content());
        }
        String m = model.isBlank() ? "gemini-1.5-flash" : model;
        String url = (baseUrl.isBlank() ? "https://generativelanguage.googleapis.com" : baseUrl) + "/v1beta/models/" + m
                + ":generateContent?key=" + apiKey;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        JsonNode n = send(req);
        String t = n.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        return t.isBlank() ? null : t;
    }

    private JsonNode send(HttpRequest req) throws Exception {
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + res.statusCode() + ": " + res.body().substring(0, Math.min(200, res.body().length())));
        }
        return mapper.readTree(res.body());
    }
}
