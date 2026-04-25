package com.xbleey.goldpricealert.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xbleey.goldpricealert.config.AiChatProperties;
import com.xbleey.goldpricealert.model.AiChatMessage;
import com.xbleey.goldpricealert.model.AiChatSession;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import com.xbleey.goldpricealert.repository.AiChatMessageStore;
import com.xbleey.goldpricealert.repository.AiChatSessionStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class AiChatService {

    private final AiChatSessionStore sessionStore;
    private final AiChatMessageStore messageStore;
    private final DeepSeekChatClient deepSeekChatClient;
    private final AiChatPromptBuilder promptBuilder;
    private final GoldPriceHistory goldPriceHistory;
    private final AiChatProperties properties;
    private final Clock clock;
    private final Executor streamExecutor;

    public AiChatService(
            AiChatSessionStore sessionStore,
            AiChatMessageStore messageStore,
            DeepSeekChatClient deepSeekChatClient,
            AiChatPromptBuilder promptBuilder,
            GoldPriceHistory goldPriceHistory,
            AiChatProperties properties,
            Clock clock,
            @Qualifier("aiChatStreamExecutor") Executor streamExecutor
    ) {
        this.sessionStore = sessionStore;
        this.messageStore = messageStore;
        this.deepSeekChatClient = deepSeekChatClient;
        this.promptBuilder = promptBuilder;
        this.goldPriceHistory = goldPriceHistory;
        this.properties = properties;
        this.clock = clock;
        this.streamExecutor = streamExecutor;
    }

    public ChatResponse chat(String username, String sessionId, String message) {
        ChatContext context = prepareContext(username, sessionId, message);
        DeepSeekChatClient.ChatResult result = deepSeekChatClient.chat(context.messages());
        persistCompletedRound(context.session(), context.userMessage(), result);
        return new ChatResponse(
                context.session().getSessionId(),
                result.content(),
                result.finishReason(),
                result.usage()
        );
    }

    public SseEmitter streamChat(String username, String sessionId, String message) {
        SseEmitter emitter = new SseEmitter(properties.getTimeout().plusSeconds(5).toMillis());
        streamExecutor.execute(() -> streamChatAsync(emitter, username, sessionId, message));
        return emitter;
    }

    public Page<AiChatSession> listSessions(String username, long pageNum, long pageSize) {
        String normalizedUsername = normalizeUsername(username);
        return sessionStore.findByUsername(normalizedUsername, pageNum, pageSize);
    }

    public Page<AiChatMessage> listMessages(String username, String sessionId, long pageNum, long pageSize) {
        AiChatSession session = findOwnedSession(normalizeUsername(username), normalizeSessionId(sessionId));
        return messageStore.findBySessionId(session.getSessionId(), pageNum, pageSize);
    }

    private void streamChatAsync(SseEmitter emitter, String username, String sessionId, String message) {
        try {
            ChatContext context = prepareContext(username, sessionId, message);
            sendEvent(emitter, "session", Map.of("sessionId", context.session().getSessionId()));
            DeepSeekChatClient.ChatResult result = deepSeekChatClient.streamChat(
                    context.messages(),
                    delta -> sendEvent(emitter, "delta", Map.of("content", delta))
            );
            persistCompletedRound(context.session(), context.userMessage(), result);
            sendEvent(emitter, "done", doneEventBody(result));
            emitter.complete();
        } catch (AiChatException ex) {
            sendErrorAndComplete(emitter, ex.statusCode(), ex.getMessage());
        } catch (Exception ex) {
            sendErrorAndComplete(emitter, "upstream_error", "AI chat failed");
        }
    }

    private ChatContext prepareContext(String username, String sessionId, String message) {
        if (!properties.hasApiKey()) {
            throw AiChatException.unavailable("DEEPSEEK_API_KEY is not configured");
        }
        String normalizedUsername = normalizeUsername(username);
        String normalizedMessage = validateMessage(message);
        AiChatSession session = resolveSession(normalizedUsername, sessionId, normalizedMessage);

        List<GoldPriceSnapshot> recentSnapshots = goldPriceHistory.getRecent(properties.getRecentSnapshotLimit());
        List<DeepSeekChatClient.Message> messages = new ArrayList<>();
        messages.add(new DeepSeekChatClient.Message("system", promptBuilder.buildSystemPrompt(recentSnapshots)));
        messages.addAll(historyMessages(session.getSessionId()));
        messages.add(new DeepSeekChatClient.Message("user", normalizedMessage));
        return new ChatContext(session, normalizedMessage, List.copyOf(messages));
    }

    private AiChatSession resolveSession(String username, String sessionId, String firstMessage) {
        String normalizedSessionId = normalizeOptionalSessionId(sessionId);
        if (normalizedSessionId != null) {
            return findOwnedSession(username, normalizedSessionId);
        }
        Instant now = clock.instant();
        AiChatSession session = new AiChatSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setUsername(username);
        session.setTitle(toTitle(firstMessage));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return sessionStore.save(session);
    }

    private AiChatSession findOwnedSession(String username, String sessionId) {
        return sessionStore.findBySessionIdAndUsername(sessionId, username)
                .orElseThrow(() -> AiChatException.notFound("chat session not found: " + sessionId));
    }

    private List<DeepSeekChatClient.Message> historyMessages(String sessionId) {
        List<AiChatMessage> recent = new ArrayList<>(
                messageStore.findRecentBySessionIdDesc(sessionId, properties.getMaxHistoryMessages())
        );
        recent.sort(Comparator.comparing(AiChatMessage::getCreatedAt).thenComparing(AiChatMessage::getId));
        return recent.stream()
                .filter(message -> isChatRole(message.getRole()))
                .filter(message -> message.getContent() != null && !message.getContent().isBlank())
                .map(message -> new DeepSeekChatClient.Message(
                        message.getRole().trim().toLowerCase(Locale.ROOT),
                        message.getContent()
                ))
                .toList();
    }

    private void persistCompletedRound(
            AiChatSession session,
            String userMessage,
            DeepSeekChatClient.ChatResult result
    ) {
        Instant userCreatedAt = clock.instant();
        AiChatMessage user = new AiChatMessage();
        user.setSessionId(session.getSessionId());
        user.setRole("user");
        user.setContent(userMessage);
        user.setCreatedAt(userCreatedAt);
        messageStore.save(user);

        Instant assistantCreatedAt = clock.instant();
        DeepSeekChatClient.Usage usage = result.usage();
        AiChatMessage assistant = new AiChatMessage();
        assistant.setSessionId(session.getSessionId());
        assistant.setRole("assistant");
        assistant.setContent(result.content());
        assistant.setModel(properties.getModel());
        assistant.setFinishReason(result.finishReason());
        assistant.setPromptTokens(usage == null ? null : usage.promptTokens());
        assistant.setCompletionTokens(usage == null ? null : usage.completionTokens());
        assistant.setTotalTokens(usage == null ? null : usage.totalTokens());
        assistant.setCreatedAt(assistantCreatedAt);
        messageStore.save(assistant);

        session.setUpdatedAt(assistantCreatedAt);
        sessionStore.update(session);
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw AiChatException.badRequest("username must not be blank");
        }
        return username.trim();
    }

    private String validateMessage(String message) {
        if (message == null) {
            throw AiChatException.badRequest("message must not be null");
        }
        String normalized = message.trim();
        if (normalized.isEmpty()) {
            throw AiChatException.badRequest("message must not be blank");
        }
        if (normalized.length() > properties.getMaxUserMessageLength()) {
            throw AiChatException.badRequest("message length must be <= " + properties.getMaxUserMessageLength());
        }
        return normalized;
    }

    private String normalizeSessionId(String sessionId) {
        String normalized = normalizeOptionalSessionId(sessionId);
        if (normalized == null) {
            throw AiChatException.badRequest("sessionId must not be blank");
        }
        return normalized;
    }

    private String normalizeOptionalSessionId(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        String normalized = sessionId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String toTitle(String message) {
        String oneLine = message.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= 40) {
            return oneLine;
        }
        return oneLine.substring(0, 40);
    }

    private boolean isChatRole(String role) {
        if (role == null) {
            return false;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return "user".equals(normalized) || "assistant".equals(normalized);
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    private void sendErrorAndComplete(SseEmitter emitter, String status, String message) {
        try {
            sendEvent(emitter, "error", Map.of(
                    "status", status,
                    "message", message
            ));
            emitter.complete();
        } catch (IOException sendEx) {
            emitter.completeWithError(sendEx);
        }
    }

    private Map<String, Object> usageMap(DeepSeekChatClient.Usage usage) {
        if (usage == null) {
            return Map.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("promptTokens", usage.promptTokens());
        body.put("completionTokens", usage.completionTokens());
        body.put("totalTokens", usage.totalTokens());
        return body;
    }

    private Map<String, Object> doneEventBody(DeepSeekChatClient.ChatResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", result.content());
        body.put("finishReason", result.finishReason());
        body.put("usage", usageMap(result.usage()));
        return body;
    }

    private record ChatContext(
            AiChatSession session,
            String userMessage,
            List<DeepSeekChatClient.Message> messages
    ) {
    }

    public record ChatResponse(
            String sessionId,
            String message,
            String finishReason,
            DeepSeekChatClient.Usage usage
    ) {
    }
}
