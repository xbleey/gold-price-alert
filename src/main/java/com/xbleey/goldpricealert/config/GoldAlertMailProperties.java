package com.xbleey.goldpricealert.config;

import lombok.Data;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.xbleey.goldpricealert.enums.GoldAlertLevel;

@Data
@Component
@ConfigurationProperties(prefix = "gold.alert.mail")
public class GoldAlertMailProperties {

    private String sender;
    private List<String> recipients = new ArrayList<>();
    private GoldAlertLevel minLevel = GoldAlertLevel.MINOR_LEVEL;
    private Map<GoldAlertLevel, Duration> cooldowns = new EnumMap<>(GoldAlertLevel.class);

    public Duration cooldownFor(GoldAlertLevel level) {
        if (level == null || cooldowns == null || cooldowns.isEmpty()) {
            return null;
        }
        return cooldowns.get(level);
    }

    public void setCooldowns(Map<GoldAlertLevel, Duration> cooldowns) {
        if (cooldowns == null) {
            this.cooldowns = new EnumMap<>(GoldAlertLevel.class);
            return;
        }
        this.cooldowns = new EnumMap<>(cooldowns);
    }

    @PostConstruct
    public void validate() {
        if (cooldowns == null || cooldowns.isEmpty()) {
            throw new IllegalStateException("gold.alert.mail.cooldowns must be configured for all levels");
        }
        for (GoldAlertLevel level : GoldAlertLevel.values()) {
            Duration duration = cooldowns.get(level);
            if (duration == null) {
                throw new IllegalStateException(
                        "gold.alert.mail.cooldowns." + level + " must be configured"
                );
            }
            if (duration.isNegative()) {
                throw new IllegalStateException(
                        "gold.alert.mail.cooldowns." + level + " must be >= 0"
                );
            }
        }
    }
}
