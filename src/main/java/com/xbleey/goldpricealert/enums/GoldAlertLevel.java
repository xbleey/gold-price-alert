package com.xbleey.goldpricealert.enums;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Duration;

@Getter
public enum GoldAlertLevel {
    INFO_LEVEL(Duration.ofMinutes(1), new BigDecimal("0.10"), "P1"),
    MINOR_LEVEL(Duration.ofMinutes(5), new BigDecimal("0.30"), "P2"),
    MODERATE_LEVEL(Duration.ofMinutes(15), new BigDecimal("0.60"), "P3"),
    MAJOR_LEVEL(Duration.ofMinutes(60), new BigDecimal("1.00"), "P4"),
    CRITICAL_LEVEL(Duration.ofMinutes(60), new BigDecimal("1.60"), "P5");

    private final Duration window;
    private final BigDecimal thresholdPercent;
    private final String levelName;

    GoldAlertLevel(Duration window, BigDecimal thresholdPercent, String levelName) {
        this.window = window;
        this.thresholdPercent = thresholdPercent;
        this.levelName = levelName;
    }

}
