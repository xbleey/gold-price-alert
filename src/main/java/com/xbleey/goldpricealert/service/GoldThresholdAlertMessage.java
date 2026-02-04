package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.enums.GoldThresholdDirection;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GoldThresholdAlertMessage(
        BigDecimal threshold,
        BigDecimal price,
        GoldThresholdDirection direction,
        Instant alertTime,
        List<GoldPriceSnapshot> recentSnapshots
) {
}
