package com.xbleey.goldpricealert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xbleey.goldpricealert.model.AiChatMessage;
import com.xbleey.goldpricealert.model.AiChatSession;
import com.xbleey.goldpricealert.service.AiChatException;
import com.xbleey.goldpricealert.service.AiChatService;
import com.xbleey.goldpricealert.service.DeepSeekChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiChatControllerTest {

    @Test
    void chatReturnsJsonResponseWhenStreamDisabled() {
        AiChatService service = mock(AiChatService.class);
        when(service.chat("admin", null, "hello")).thenReturn(new AiChatService.ChatResponse(
                "session-1",
                "answer",
                "stop",
                new DeepSeekChatClient.Usage(1, 2, 3)
        ));
        AiChatController controller = new AiChatController(service);

        ResponseEntity<?> response = controller.chat(
                new AiChatController.ChatRequest(null, "hello", false),
                auth()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("status", "ok");
        assertThat(body).containsEntry("sessionId", "session-1");
        assertThat(body).containsEntry("message", "answer");
        @SuppressWarnings("unchecked")
        Map<String, Object> usage = (Map<String, Object>) body.get("usage");
        assertThat(usage).containsEntry("totalTokens", 3);
    }

    @Test
    void chatReturnsSseEmitterWhenStreamEnabled() {
        AiChatService service = mock(AiChatService.class);
        SseEmitter emitter = new SseEmitter();
        when(service.streamChat("admin", "session-1", "hello")).thenReturn(emitter);
        AiChatController controller = new AiChatController(service);

        ResponseEntity<?> response = controller.chat(
                new AiChatController.ChatRequest("session-1", "hello", true),
                auth()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        assertThat(response.getBody()).isSameAs(emitter);
        verify(service).streamChat("admin", "session-1", "hello");
    }

    @Test
    void chatReturnsBadRequestForMissingBody() {
        AiChatService service = mock(AiChatService.class);
        AiChatController controller = new AiChatController(service);

        ResponseEntity<?> response = controller.chat(null, auth());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("status", "bad_request");
        verifyNoInteractions(service);
    }

    @Test
    void chatMapsServiceValidationError() {
        AiChatService service = mock(AiChatService.class);
        when(service.chat("admin", null, "")).thenThrow(AiChatException.badRequest("message must not be blank"));
        AiChatController controller = new AiChatController(service);

        ResponseEntity<?> response = controller.chat(
                new AiChatController.ChatRequest(null, "", false),
                auth()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("status", "bad_request");
    }

    @Test
    void listSessionsReturnsPagedRecords() {
        AiChatService service = mock(AiChatService.class);
        Page<AiChatSession> page = Page.of(1, 20);
        AiChatSession session = new AiChatSession();
        session.setSessionId("session-1");
        session.setTitle("title");
        session.setCreatedAt(Instant.parse("2026-04-25T00:00:00Z"));
        session.setUpdatedAt(Instant.parse("2026-04-25T00:01:00Z"));
        page.setRecords(List.of(session));
        page.setTotal(1);
        when(service.listSessions("admin", 1, 20)).thenReturn(page);
        AiChatController controller = new AiChatController(service);

        ResponseEntity<Map<String, Object>> response = controller.listSessions(1, 20, auth());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("total", 1L);
    }

    @Test
    void listMessagesMapsMissingSession() {
        AiChatService service = mock(AiChatService.class);
        when(service.listMessages("admin", "missing", 1, 50)).thenThrow(AiChatException.notFound("chat session not found: missing"));
        AiChatController controller = new AiChatController(service);

        ResponseEntity<Map<String, Object>> response = controller.listMessages("missing", 1, 50, auth());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", "not_found");
    }

    @Test
    void listMessagesReturnsPagedRecords() {
        AiChatService service = mock(AiChatService.class);
        Page<AiChatMessage> page = Page.of(1, 50);
        AiChatMessage message = new AiChatMessage();
        message.setId(1L);
        message.setSessionId("session-1");
        message.setRole("user");
        message.setContent("hello");
        message.setCreatedAt(Instant.parse("2026-04-25T00:00:00Z"));
        page.setRecords(List.of(message));
        page.setTotal(1);
        when(service.listMessages("admin", "session-1", 1, 50)).thenReturn(page);
        AiChatController controller = new AiChatController(service);

        ResponseEntity<Map<String, Object>> response = controller.listMessages("session-1", 1, 50, auth());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("total", 1L);
    }

    private Authentication auth() {
        return UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of());
    }
}
