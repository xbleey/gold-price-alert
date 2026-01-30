package com.xbleey.goldpricealert.enums;

import java.time.Duration;

public enum GoldAlertLevel {
    INFO_LEVEL(Duration.ofMinutes(1), 0.05),
    MINOR_LEVEL(Duration.ofMinutes(1), 0.10),
    MODERATE_LEVEL(Duration.ofMinutes(5), 0.25),
    MAJOR_LEVEL(Duration.ofMinutes(5), 0.50),
    CRITICAL_LEVEL(Duration.ofMinutes(15), 1.00);

    private final Duration window;
    private final double thresholdPercent;

    GoldAlertLevel(Duration window, double thresholdPercent) {
        this.window = window;
        this.thresholdPercent = thresholdPercent;
    }

    public Duration getWindow() {
        return window;
    }

    public double getThresholdPercent() {
        return thresholdPercent;
    }
}
