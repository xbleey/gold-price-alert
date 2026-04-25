package com.xbleey.goldpricealert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xbleey.goldpricealert.model.AiChatMessage;
import com.xbleey.goldpricealert.model.AiChatSession;
import com.xbleey.goldpricealert.service.AiChatException;
import com.xbleey.goldpricealert.service.AiChatService;
import com.xbleey.goldpricealert.service.DeepSeekChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/ai/chat")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping
    public ResponseEntity<?> chat(
            @RequestBody(required = false) ChatRequest request,
            Authentication authentication
    ) {
        if (request == null) {
            return badRequest("request body must not be null");
        }
        try {
            String username = authenticatedUsername(authentication);
            if (Boolean.TRUE.equals(request.stream())) {
                SseEmitter emitter = aiChatService.streamChat(username, request.sessionId(), request.message());
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(emitter);
            }
            AiChatService.ChatResponse response = aiChatService.chat(username, request.sessionId(), request.message());
            return ResponseEntity.ok(chatResponseBody(response));
        } catch (AiChatException ex) {
            return response(ex.httpStatus(), ex.statusCode(), ex.getMessage());
        } catch (NoSuchElementException ex) {
            return notFound(ex.getMessage());
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions(
            @RequestParam(name = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(name = "pageSize", defaultValue = "20") long pageSize,
            Authentication authentication
    ) {
        try {
            Page<AiChatSession> page = aiChatService.listSessions(authenticatedUsername(authentication), pageNum, pageSize);
            List<SessionResponse> records = page.getRecords().stream()
                    .map(this::toSessionResponse)
                    .toList();
            return ResponseEntity.ok(Map.of(
                    "current", page.getCurrent(),
                    "pageSize", page.getSize(),
                    "total", page.getTotal(),
                    "pages", page.getPages(),
                    "records", records
            ));
        } catch (AiChatException ex) {
            return response(ex.httpStatus(), ex.statusCode(), ex.getMessage());
        }
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<Map<String, Object>> listMessages(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(name = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(name = "pageSize", defaultValue = "50") long pageSize,
            Authentication authentication
    ) {
        try {
            Page<AiChatMessage> page = aiChatService.listMessages(
                    authenticatedUsername(authentication),
                    sessionId,
                    pageNum,
                    pageSize
            );
            List<MessageResponse> records = page.getRecords().stream()
                    .map(this::toMessageResponse)
                    .toList();
            return ResponseEntity.ok(Map.of(
                    "current", page.getCurrent(),
                    "pageSize", page.getSize(),
                    "total", page.getTotal(),
                    "pages", page.getPages(),
                    "records", records
            ));
        } catch (AiChatException ex) {
            return response(ex.httpStatus(), ex.statusCode(), ex.getMessage());
        } catch (NoSuchElementException ex) {
            return notFound(ex.getMessage());
        }
    }

    private String authenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw AiChatException.badRequest("authenticated user is required");
        }
        return authentication.getName();
    }

    private SessionResponse toSessionResponse(AiChatSession session) {
        return new SessionResponse(
                session.getSessionId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private MessageResponse toMessageResponse(AiChatMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getSessionId(),
                message.getRole(),
                message.getContent(),
                message.getModel(),
                message.getFinishReason(),
                message.getPromptTokens(),
                message.getCompletionTokens(),
                message.getTotalTokens(),
                message.getCreatedAt()
        );
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

    private Map<String, Object> chatResponseBody(AiChatService.ChatResponse response) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("sessionId", response.sessionId());
        body.put("message", response.message());
        body.put("finishReason", response.finishReason());
        body.put("usage", usageMap(response.usage()));
        return body;
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return response(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    private ResponseEntity<Map<String, Object>> notFound(String message) {
        return response(HttpStatus.NOT_FOUND, "not_found", message);
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    public record ChatRequest(String sessionId, String message, Boolean stream) {
    }

    public record SessionResponse(String sessionId, String title, Instant createdAt, Instant updatedAt) {
    }

    public record MessageResponse(
            Long id,
            String sessionId,
            String role,
            String content,
            String model,
            String finishReason,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Instant createdAt
    ) {
    }
}
