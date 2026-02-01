package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Accessors(fluent = true)
public record GoldAlertMessage(GoldAlertLevel level, String message, Instant alertTime, Duration window,
                               BigDecimal thresholdPercent, BigDecimal changePercent, BigDecimal baselinePrice,
                               BigDecimal latestPrice, List<GoldPriceSnapshot> recentSnapshots) {
}
