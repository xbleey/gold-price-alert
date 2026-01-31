package com.xbleey.goldpricealert.model;

import java.math.BigDecimal;
import java.time.Instant;

public record GoldApiResponse(
        String name,
        BigDecimal price,
        String symbol,
        Instant updatedAt,
        String updatedAtReadable
) {
}
