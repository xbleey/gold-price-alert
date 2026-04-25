package com.xbleey.goldpricealert.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbleey.goldpricealert.config.AiChatProperties;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekChatClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void chatSendsDeepSeekRequestAndParsesResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "choices": [
                            {"finish_reason": "stop", "message": {"content": "黄金回答"}}
                          ],
                          "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                        }
                        """));
        DeepSeekChatClient client = newClient();

        DeepSeekChatClient.ChatResult result = client.chat(List.of(
                new DeepSeekChatClient.Message("system", "system prompt"),
                new DeepSeekChatClient.Message("user", "hello")
        ));

        assertThat(result.content()).isEqualTo("黄金回答");
        assertThat(result.finishReason()).isEqualTo("stop");
        assertThat(result.usage().totalTokens()).isEqualTo(15);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        assertThat(body.path("model").asText()).isEqualTo("deepseek-v4-flash");
        assertThat(body.path("stream").asBoolean()).isFalse();
        assertThat(body.path("thinking").path("type").asText()).isEqualTo("disabled");
        assertThat(body.path("messages").size()).isEqualTo(2);
    }

    @Test
    void streamChatForwardsDeltasAndParsesFinalUsage() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("""
                        data: {"choices":[{"delta":{"content":"你"},"finish_reason":null}],"usage":null}

                        data: {"choices":[{"delta":{"content":"好"},"finish_reason":null}],"usage":null}

                        data: {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":null}

                        data: {"choices":[],"usage":{"prompt_tokens":4,"completion_tokens":5,"total_tokens":9}}

                        data: [DONE]

                        """));
        DeepSeekChatClient client = newClient();
        List<String> deltas = new ArrayList<>();

        DeepSeekChatClient.ChatResult result = client.streamChat(
                List.of(new DeepSeekChatClient.Message("user", "hello")),
                deltas::add
        );

        assertThat(deltas).containsExactly("你", "好");
        assertThat(result.content()).isEqualTo("你好");
        assertThat(result.finishReason()).isEqualTo("stop");
        assertThat(result.usage().promptTokens()).isEqualTo(4);
        assertThat(result.usage().completionTokens()).isEqualTo(5);
        assertThat(result.usage().totalTokens()).isEqualTo(9);

        RecordedRequest request = server.takeRequest();
        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        assertThat(body.path("stream").asBoolean()).isTrue();
        assertThat(body.path("stream_options").path("include_usage").asBoolean()).isTrue();
    }

    private DeepSeekChatClient newClient() {
        AiChatProperties properties = new AiChatProperties();
        properties.setApiUrl(server.url("/chat/completions").uri());
        properties.setApiKey("test-key");
        properties.setTimeout(Duration.ofSeconds(5));
        return new DeepSeekChatClient(new OkHttpClient(), objectMapper, properties);
    }
}
