package com.xbleey.goldpricealert.config;

import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import com.xbleey.goldpricealert.service.GoldAlertLevelName;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gold.alert.mail")
public class GoldAlertMailProperties {

    private String sender;
    // 兼容历史配置值（如 MODERATE_LEVEL）和新配置值（如 P3）
    private String minLevel = GoldAlertLevel.MODERATE_LEVEL.name();

    public int resolveMinLevelRank() {
        if (minLevel == null || minLevel.isBlank()) {
            return 1;
        }
        String trimmed = minLevel.trim();
        if (GoldAlertLevelName.isValid(trimmed)) {
            return GoldAlertLevelName.rankOf(trimmed);
        }
        for (GoldAlertLevel level : GoldAlertLevel.values()) {
            if (level.name().equalsIgnoreCase(trimmed)) {
                return GoldAlertLevelName.rankOf(level.getLevelName());
            }
            if (level.getLevelName().equalsIgnoreCase(trimmed)) {
                return GoldAlertLevelName.rankOf(level.getLevelName());
            }
        }
        throw new IllegalArgumentException("invalid gold.alert.mail.min-level: " + minLevel);
    }
}
