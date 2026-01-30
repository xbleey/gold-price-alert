package com.xbleey.goldpricealert.enums;

import lombok.Getter;

import java.time.Duration;

@Getter
public enum GoldAlertLevel {
    INFO_LEVEL(Duration.ofMinutes(1), 0.08),
    MINOR_LEVEL(Duration.ofMinutes(5), 0.20),
    MODERATE_LEVEL(Duration.ofMinutes(15), 0.45),
    MAJOR_LEVEL(Duration.ofMinutes(60), 0.90),
    CRITICAL_LEVEL(Duration.ofMinutes(60), 1.50);

    private final Duration window;
    private final double thresholdPercent;

    GoldAlertLevel(Duration window, double thresholdPercent) {
        this.window = window;
        this.thresholdPercent = thresholdPercent;
    }

}
