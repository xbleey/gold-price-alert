package com.xbleey.goldpricealert.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xbleey.goldpricealert.config.AiChatProperties;
import com.xbleey.goldpricealert.model.AiChatMessage;
import com.xbleey.goldpricealert.model.AiChatSession;
import com.xbleey.goldpricealert.model.GoldApiResponse;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import com.xbleey.goldpricealert.repository.AiChatMessageStore;
import com.xbleey.goldpricealert.repository.AiChatSessionStore;
import com.xbleey.goldpricealert.support.InMemoryGoldPriceSnapshotStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiChatServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-25T00:00:00Z");

    @Test
    void chatCreatesSessionBuildsGoldPromptAndPersistsRound() {
        Fixture fixture = newFixture();
        ArgumentCaptor<List<DeepSeekChatClient.Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        when(fixture.client.chat(messagesCaptor.capture())).thenReturn(new DeepSeekChatClient.ChatResult(
                "可以关注美元和实际利率。",
                "stop",
                new DeepSeekChatClient.Usage(10, 8, 18)
        ));

        AiChatService.ChatResponse response = fixture.service.chat("admin", null, " 黄金现在怎么看 ");

        assertThat(response.sessionId()).isNotBlank();
        assertThat(response.message()).isEqualTo("可以关注美元和实际利率。");
        assertThat(fixture.sessionStore.sessions).hasSize(1);
        assertThat(fixture.messageStore.messages).hasSize(2);
        assertThat(fixture.messageStore.messages.get(0).getRole()).isEqualTo("user");
        assertThat(fixture.messageStore.messages.get(1).getRole()).isEqualTo("assistant");
        assertThat(fixture.messageStore.messages.get(1).getTotalTokens()).isEqualTo(18);

        List<DeepSeekChatClient.Message> messages = messagesCaptor.getValue();
        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(messages.get(0).content()).contains("黄金市场中文对话助手", "4888.12");
        assertThat(messages.get(1).role()).isEqualTo("user");
        assertThat(messages.get(1).content()).isEqualTo("黄金现在怎么看");
    }

    @Test
    void chatUsesExistingHistoryAndRequiresSessionOwnership() {
        Fixture fixture = newFixture();
        AiChatSession session = fixture.sessionStore.create("session-1", "admin", "title", NOW.minusSeconds(60));
        fixture.messageStore.add(session.getSessionId(), "user", "上一轮问题", NOW.minusSeconds(50));
        fixture.messageStore.add(session.getSessionId(), "assistant", "上一轮回答", NOW.minusSeconds(40));
        ArgumentCaptor<List<DeepSeekChatClient.Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        when(fixture.client.chat(messagesCaptor.capture())).thenReturn(new DeepSeekChatClient.ChatResult("新的回答", "stop", null));

        fixture.service.chat("admin", "session-1", "继续分析");

        assertThat(messagesCaptor.getValue())
                .extracting(DeepSeekChatClient.Message::role)
                .containsExactly("system", "user", "assistant", "user");
        assertThat(messagesCaptor.getValue().get(1).content()).isEqualTo("上一轮问题");
        assertThat(messagesCaptor.getValue().get(2).content()).isEqualTo("上一轮回答");

        assertThatThrownBy(() -> fixture.service.chat("other", "session-1", "继续分析"))
                .isInstanceOf(AiChatException.class)
                .hasMessageContaining("chat session not found");
    }

    @Test
    void streamChatPersistsMessagesOnlyAfterCompletion() {
        Fixture fixture = newFixture();
        doAnswer(invocation -> {
            DeepSeekChatClient.StreamDeltaHandler handler = invocation.getArgument(1);
            handler.onDelta("你");
            handler.onDelta("好");
            return new DeepSeekChatClient.ChatResult("你好", "stop", new DeepSeekChatClient.Usage(1, 2, 3));
        }).when(fixture.client).streamChat(any(), any());

        fixture.service.streamChat("admin", null, "流式回答");

        assertThat(fixture.sessionStore.sessions).hasSize(1);
        assertThat(fixture.messageStore.messages).hasSize(2);
        assertThat(fixture.messageStore.messages.get(0).getContent()).isEqualTo("流式回答");
        assertThat(fixture.messageStore.messages.get(1).getContent()).isEqualTo("你好");
    }

    @Test
    void streamChatDoesNotPersistCurrentRoundWhenUpstreamFails() {
        Fixture fixture = newFixture();
        when(fixture.client.streamChat(any(), any())).thenThrow(
                AiChatException.upstreamUnavailable("failed", null)
        );

        fixture.service.streamChat("admin", null, "流式失败");

        assertThat(fixture.sessionStore.sessions).hasSize(1);
        assertThat(fixture.messageStore.messages).isEmpty();
    }

    @Test
    void chatRejectsMissingApiKeyBeforePersisting() {
        Fixture fixture = newFixture();
        fixture.properties.setApiKey("");

        assertThatThrownBy(() -> fixture.service.chat("admin", null, "hello"))
                .isInstanceOf(AiChatException.class)
                .hasMessageContaining("DEEPSEEK_API_KEY");
        assertThat(fixture.sessionStore.sessions).isEmpty();
        assertThat(fixture.messageStore.messages).isEmpty();
    }

    private Fixture newFixture() {
        AiChatProperties properties = new AiChatProperties();
        properties.setApiKey("key");
        properties.setTimeout(Duration.ofSeconds(5));
        InMemoryAiChatSessionStore sessionStore = new InMemoryAiChatSessionStore();
        InMemoryAiChatMessageStore messageStore = new InMemoryAiChatMessageStore();
        DeepSeekChatClient client = mock(DeepSeekChatClient.class);
        InMemoryGoldPriceSnapshotStore snapshotStore = new InMemoryGoldPriceSnapshotStore();
        snapshotStore.save(snapshot());
        GoldPriceHistory history = new GoldPriceHistory(snapshotStore);
        AiChatService service = new AiChatService(
                sessionStore,
                messageStore,
                client,
                new AiChatPromptBuilder(),
                history,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Runnable::run
        );
        return new Fixture(service, sessionStore, messageStore, client, properties);
    }

    private GoldPriceSnapshot snapshot() {
        Instant time = Instant.parse("2026-04-24T23:59:00Z");
        return new GoldPriceSnapshot(time, new GoldApiResponse(
                "gold",
                new BigDecimal("4888.12"),
                "XAU/USD",
                time,
                "2026-04-24 23:59:00"
        ));
    }

    private record Fixture(
            AiChatService service,
            InMemoryAiChatSessionStore sessionStore,
            InMemoryAiChatMessageStore messageStore,
            DeepSeekChatClient client,
            AiChatProperties properties
    ) {
    }

    private static class InMemoryAiChatSessionStore implements AiChatSessionStore {

        private final List<AiChatSession> sessions = new ArrayList<>();
        private long sequence = 1;

        @Override
        public AiChatSession save(AiChatSession session) {
            session.setId(sequence++);
            sessions.add(session);
            return session;
        }

        @Override
        public int update(AiChatSession session) {
            return 1;
        }

        @Override
        public Optional<AiChatSession> findBySessionIdAndUsername(String sessionId, String username) {
            return sessions.stream()
                    .filter(session -> session.getSessionId().equals(sessionId))
                    .filter(session -> session.getUsername().equals(username))
                    .findFirst();
        }

        @Override
        public Page<AiChatSession> findByUsername(String username, long pageNum, long pageSize) {
            Page<AiChatSession> page = Page.of(pageNum, pageSize);
            List<AiChatSession> records = sessions.stream()
                    .filter(session -> session.getUsername().equals(username))
                    .sorted(Comparator.comparing(AiChatSession::getUpdatedAt).reversed())
                    .toList();
            page.setRecords(records);
            page.setTotal(records.size());
            return page;
        }

        private AiChatSession create(String sessionId, String username, String title, Instant now) {
            AiChatSession session = new AiChatSession();
            session.setSessionId(sessionId);
            session.setUsername(username);
            session.setTitle(title);
            session.setCreatedAt(now);
            session.setUpdatedAt(now);
            return save(session);
        }
    }

    private static class InMemoryAiChatMessageStore implements AiChatMessageStore {

        private final List<AiChatMessage> messages = new ArrayList<>();
        private long sequence = 1;

        @Override
        public AiChatMessage save(AiChatMessage message) {
            message.setId(sequence++);
            messages.add(message);
            return message;
        }

        @Override
        public List<AiChatMessage> findRecentBySessionIdDesc(String sessionId, int limit) {
            return messages.stream()
                    .filter(message -> message.getSessionId().equals(sessionId))
                    .sorted(Comparator.comparing(AiChatMessage::getCreatedAt).reversed())
                    .limit(Math.max(0, limit))
                    .toList();
        }

        @Override
        public Page<AiChatMessage> findBySessionId(String sessionId, long pageNum, long pageSize) {
            Page<AiChatMessage> page = Page.of(pageNum, pageSize);
            List<AiChatMessage> records = messages.stream()
                    .filter(message -> message.getSessionId().equals(sessionId))
                    .sorted(Comparator.comparing(AiChatMessage::getCreatedAt))
                    .toList();
            page.setRecords(records);
            page.setTotal(records.size());
            return page;
        }

        private AiChatMessage add(String sessionId, String role, String content, Instant createdAt) {
            AiChatMessage message = new AiChatMessage();
            message.setSessionId(sessionId);
            message.setRole(role);
            message.setContent(content);
            message.setCreatedAt(createdAt);
            return save(message);
        }
    }
}
