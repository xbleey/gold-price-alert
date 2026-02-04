package com.xbleey.goldpricealert.config;

import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "gold.alert.window")
public class GoldAlertWindowProperties {

    private Map<GoldAlertLevel, Duration> levels = new EnumMap<>(GoldAlertLevel.class);

    public Duration windowFor(GoldAlertLevel level) {
        if (level == null || levels == null || levels.isEmpty()) {
            return null;
        }
        return levels.get(level);
    }

    public void setLevels(Map<GoldAlertLevel, Duration> levels) {
        if (levels == null) {
            this.levels = new EnumMap<>(GoldAlertLevel.class);
            return;
        }
        this.levels = new EnumMap<>(levels);
    }

    @PostConstruct
    public void validate() {
        if (levels == null || levels.isEmpty()) {
            throw new IllegalStateException("gold.alert.window.levels must be configured for all levels");
        }
        for (GoldAlertLevel level : GoldAlertLevel.values()) {
            Duration duration = levels.get(level);
            if (duration == null) {
                throw new IllegalStateException(
                        "gold.alert.window.levels." + level + " must be configured"
                );
            }
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalStateException(
                        "gold.alert.window.levels." + level + " must be > 0"
                );
            }
        }
    }
}
