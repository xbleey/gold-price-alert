package com.xbleey.goldpricealert.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;

@Data
@Component
@ConfigurationProperties(prefix = "gold.ai")
public class AiChatProperties {

    private URI apiUrl = URI.create("https://api.deepseek.com/chat/completions");
    private String apiKey = "";
    private String model = "deepseek-v4-flash";
    private String thinking = "disabled";
    private BigDecimal temperature = new BigDecimal("0.4");
    private Integer maxTokens = 2048;
    private Duration timeout = Duration.ofSeconds(60);
    private Integer maxHistoryMessages = 20;
    private Integer recentSnapshotLimit = 5;
    private Integer maxUserMessageLength = 4000;

    @PostConstruct
    public void validate() {
        if (apiUrl == null) {
            throw new IllegalStateException("gold.ai.api-url must be configured");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("gold.ai.model must not be blank");
        }
        String normalizedThinking = thinking == null ? "" : thinking.trim().toLowerCase(Locale.ROOT);
        if (!"enabled".equals(normalizedThinking) && !"disabled".equals(normalizedThinking)) {
            throw new IllegalStateException("gold.ai.thinking must be enabled or disabled");
        }
        thinking = normalizedThinking;
        if (temperature == null || temperature.compareTo(BigDecimal.ZERO) < 0 || temperature.compareTo(new BigDecimal("2")) > 0) {
            throw new IllegalStateException("gold.ai.temperature must be between 0 and 2");
        }
        if (maxTokens == null || maxTokens <= 0) {
            throw new IllegalStateException("gold.ai.max-tokens must be > 0");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("gold.ai.timeout must be > 0");
        }
        if (maxHistoryMessages == null || maxHistoryMessages < 0) {
            throw new IllegalStateException("gold.ai.max-history-messages must be >= 0");
        }
        if (recentSnapshotLimit == null || recentSnapshotLimit < 0) {
            throw new IllegalStateException("gold.ai.recent-snapshot-limit must be >= 0");
        }
        if (maxUserMessageLength == null || maxUserMessageLength <= 0) {
            throw new IllegalStateException("gold.ai.max-user-message-length must be > 0");
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String apiKeyValue() {
        return apiKey == null ? "" : apiKey.trim();
    }
}
