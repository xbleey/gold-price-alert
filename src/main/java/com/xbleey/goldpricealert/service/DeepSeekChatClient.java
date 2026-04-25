package com.xbleey.goldpricealert.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbleey.goldpricealert.config.AiChatProperties;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class DeepSeekChatClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final AiChatProperties properties;

    public DeepSeekChatClient(OkHttpClient okHttpClient, ObjectMapper objectMapper, AiChatProperties properties) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ChatResult chat(List<Message> messages) {
        Request request = buildRequest(messages, false);
        try (Response response = client().newCall(request).execute()) {
            ensureSuccessful(response);
            ResponseBody body = response.body();
            if (body == null) {
                throw AiChatException.upstreamError("DeepSeek API returned empty response body", null);
            }
            JsonNode root = objectMapper.readTree(body.byteStream());
            JsonNode choice = firstChoice(root);
            String content = textOrEmpty(choice.path("message").path("content"));
            return new ChatResult(
                    content,
                    textOrNull(choice.path("finish_reason")),
                    parseUsage(root.path("usage"))
            );
        } catch (AiChatException ex) {
            throw ex;
        } catch (IOException ex) {
            throw AiChatException.upstreamUnavailable("failed to call DeepSeek API", ex);
        }
    }

    public ChatResult streamChat(List<Message> messages, StreamDeltaHandler deltaHandler) {
        Request request = buildRequest(messages, true);
        StringBuilder content = new StringBuilder();
        String finishReason = null;
        Usage usage = null;
        try (Response response = client().newCall(request).execute()) {
            ensureSuccessful(response);
            ResponseBody body = response.body();
            if (body == null) {
                throw AiChatException.upstreamError("DeepSeek API returned empty response body", null);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String payload = parseDataLine(line);
                    if (payload == null) {
                        continue;
                    }
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    JsonNode root = objectMapper.readTree(payload);
                    Usage eventUsage = parseUsage(root.path("usage"));
                    if (eventUsage != null) {
                        usage = eventUsage;
                    }
                    JsonNode choices = root.path("choices");
                    if (!choices.isArray() || choices.isEmpty()) {
                        continue;
                    }
                    JsonNode choice = choices.get(0);
                    String eventFinishReason = textOrNull(choice.path("finish_reason"));
                    if (eventFinishReason != null) {
                        finishReason = eventFinishReason;
                    }
                    String delta = textOrNull(choice.path("delta").path("content"));
                    if (delta != null && !delta.isEmpty()) {
                        content.append(delta);
                        deltaHandler.onDelta(delta);
                    }
                }
            }
            return new ChatResult(content.toString(), finishReason, usage);
        } catch (AiChatException ex) {
            throw ex;
        } catch (IOException ex) {
            throw AiChatException.upstreamUnavailable("failed to call DeepSeek API", ex);
        }
    }

    private Request buildRequest(List<Message> messages, boolean stream) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        ArrayNode messageNodes = root.putArray("messages");
        for (Message message : messages) {
            ObjectNode messageNode = messageNodes.addObject();
            messageNode.put("role", message.role());
            messageNode.put("content", message.content());
        }
        root.putObject("thinking").put("type", properties.getThinking());
        root.put("temperature", properties.getTemperature());
        root.put("max_tokens", properties.getMaxTokens());
        root.put("stream", stream);
        if (stream) {
            root.putObject("stream_options").put("include_usage", true);
        }

        RequestBody body = RequestBody.create(root.toString(), JSON);
        return new Request.Builder()
                .url(properties.getApiUrl().toString())
                .header("Authorization", "Bearer " + properties.apiKeyValue())
                .header("Content-Type", "application/json")
                .post(body)
                .build();
    }

    private OkHttpClient client() {
        return okHttpClient.newBuilder()
                .callTimeout(properties.getTimeout())
                .readTimeout(properties.getTimeout())
                .build();
    }

    private void ensureSuccessful(Response response) {
        if (response.isSuccessful()) {
            return;
        }
        String message = "DeepSeek API returned HTTP status " + response.code();
        if (response.code() == 429 || response.code() == 500 || response.code() == 503) {
            throw AiChatException.upstreamUnavailable(message, null);
        }
        throw AiChatException.upstreamError(message, null);
    }

    private JsonNode firstChoice(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw AiChatException.upstreamError("DeepSeek API response missing choices", null);
        }
        return choices.get(0);
    }

    private Usage parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isMissingNode() || usageNode.isNull()) {
            return null;
        }
        return new Usage(
                intOrNull(usageNode.path("prompt_tokens")),
                intOrNull(usageNode.path("completion_tokens")),
                intOrNull(usageNode.path("total_tokens"))
        );
    }

    private Integer intOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asInt();
    }

    private String textOrEmpty(JsonNode node) {
        String value = textOrNull(node);
        return value == null ? "" : value;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private String parseDataLine(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("data:")) {
            return null;
        }
        return trimmed.substring("data:".length()).trim();
    }

    public record Message(String role, String content) {
    }

    public record Usage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    }

    public record ChatResult(String content, String finishReason, Usage usage) {
    }

    @FunctionalInterface
    public interface StreamDeltaHandler {
        void onDelta(String content) throws IOException;
    }
}
