package com.xbleey.goldpricealert.enums;

import lombok.Getter;

import java.time.Duration;

@Getter
public enum GoldAlertLevel {
    INFO_LEVEL(Duration.ofMinutes(1), 0.08, "P1"),
    MINOR_LEVEL(Duration.ofMinutes(5), 0.20, "P2"),
    MODERATE_LEVEL(Duration.ofMinutes(15), 0.45, "P3"),
    MAJOR_LEVEL(Duration.ofMinutes(60), 0.90, "P4"),
    CRITICAL_LEVEL(Duration.ofMinutes(60), 1.50, "P5");

    private final Duration window;
    private final double thresholdPercent;
    private final String levelName;

    GoldAlertLevel(Duration window, double thresholdPercent, String levelName) {
        this.window = window;
        this.thresholdPercent = thresholdPercent;
        this.levelName = levelName;
    }

}
