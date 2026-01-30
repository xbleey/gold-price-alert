package com.xbleey.goldpricealert.model;

import java.time.Instant;

public record GoldApiResponse(
        String name,
        double price,
        String symbol,
        Instant updatedAt,
        String updatedAtReadable
) {
}
