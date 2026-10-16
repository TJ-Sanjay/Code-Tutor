package com.codetutor.backend.chat;

import com.codetutor.backend.analysis.Knowledge;
import com.codetutor.backend.common.response.ApiResponse;
import com.codetutor.backend.project.service.ProjectAccess;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Codebase Q&amp;A. History is stored per (project, user) — a user can only ever read their own conversations.
 */
@RestController
@RequestMapping("/projects/{id}/chat")
public class ChatController {

    public record ChatRequest(String query, ChatEngine.Selection selection) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MessageView(Long id, String role, String content, JsonNode sources, JsonNode flow, JsonNode actions, String createdAt) {}

    private final ProjectAccess access;
    private final ChatMessageRepository messages;
    private final LlmClient llm;
    private final ObjectMapper mapper;

    public ChatController(ProjectAccess access, ChatMessageRepository messages, LlmClient llm, ObjectMapper mapper) {
        this.access = access;
        this.messages = messages;
        this.llm = llm;
        this.mapper = mapper;
    }

    @PostMapping
    public ApiResponse<MessageView> ask(@AuthenticationPrincipal UserDetails u, @PathVariable long id, @RequestBody ChatRequest req) throws Exception {
        if (req == null || req.query() == null || req.query().isBlank()) throw new IllegalArgumentException("Please type a question.");
        if (req.query().length() > 4000) throw new IllegalArgumentException("The question is too long (max 4000 characters).");
        ProjectAccess.Ctx ctx = access.ready(u.getUsername(), id);
        Long userId = ctx.user().getId();

        List<LlmClient.Turn> history = new ArrayList<>();
        for (ChatMessage m : messages.findByProjectIdAndUserIdOrderByIdAsc(id, userId)) {
            history.add(new LlmClient.Turn(m.getRole().equals("USER") ? "user" : "assistant", m.getContent()));
        }
        messages.save(new ChatMessage(ctx.project(), ctx.user(), "USER", req.query().trim(), null, null));

        ChatEngine.Answer a = new ChatEngine(ctx.k(), llm).answer(req.query(), req.selection(), history);
        String sources = mapper.writeValueAsString(a.sources());
        String flow = a.flow() == null ? null : mapper.writeValueAsString(a.flow());
        String actions = a.actions() == null || a.actions().isEmpty() ? null : mapper.writeValueAsString(a.actions());
        // actions are stored inside the sources json envelope to avoid another column
        String envelope = mapper.writeValueAsString(java.util.Map.of("sources", mapper.readTree(sources),
                "actions", actions == null ? mapper.createArrayNode() : mapper.readTree(actions)));
        ChatMessage saved = messages.save(new ChatMessage(ctx.project(), ctx.user(), "ASSISTANT", a.answer(), envelope, flow));
        return ApiResponse.success("Answer", view(saved));
    }

    @GetMapping("/history")
    public ApiResponse<List<MessageView>> history(@AuthenticationPrincipal UserDetails u, @PathVariable long id) throws Exception {
        ProjectAccess.Ctx ctx = access.ready(u.getUsername(), id);
        List<MessageView> out = new ArrayList<>();
        for (ChatMessage m : messages.findByProjectIdAndUserIdOrderByIdAsc(id, ctx.user().getId())) out.add(view(m));
        return ApiResponse.success("History", out);
    }

    @DeleteMapping("/history")
    public ApiResponse<Void> clear(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        ProjectAccess.Ctx ctx = access.ready(u.getUsername(), id);
        messages.deleteByProjectIdAndUserId(id, ctx.user().getId());
        return ApiResponse.success("Chat cleared", null);
    }

    public record Suggestions(List<String> questions, String provider) {}

    @GetMapping("/suggestions")
    public ApiResponse<Suggestions> suggestions(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        Knowledge k = access.ready(u.getUsername(), id).k();
        return ApiResponse.success("Suggestions", new Suggestions(new ChatEngine(k, llm).suggestions(), llm.provider()));
    }

    private MessageView view(ChatMessage m) throws Exception {
        JsonNode sources = null;
        JsonNode actions = null;
        if (m.getSourcesJson() != null) {
            JsonNode env = mapper.readTree(m.getSourcesJson());
            sources = env.has("sources") ? env.get("sources") : env;
            actions = env.has("actions") ? env.get("actions") : null;
        }
        JsonNode flow = m.getFlowJson() == null ? null : mapper.readTree(m.getFlowJson());
        return new MessageView(m.getId(), m.getRole(), m.getContent(), sources, flow, actions,
                m.getCreatedAt() == null ? null : m.getCreatedAt().toString());
    }
}
