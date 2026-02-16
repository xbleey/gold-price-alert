package com.xbleey.goldpricealert.service;

import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Duration;

@Accessors(fluent = true)
public record GoldAlertLevelConfig(
        String levelName,
        int levelRank,
        BigDecimal thresholdPercent,
        int windowMinutes,
        int cooldownMinutes,
        boolean protectedLevel
) {

    public Duration windowDuration() {
        return Duration.ofMinutes(windowMinutes);
    }

    public Duration cooldownDuration() {
        return Duration.ofMinutes(cooldownMinutes);
    }
}
