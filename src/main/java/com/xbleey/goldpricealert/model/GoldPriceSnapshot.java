package com.xbleey.goldpricealert.model;

import java.math.BigDecimal;
import java.time.Instant;

public record GoldPriceSnapshot(Instant fetchedAt, GoldApiResponse response) {

    public BigDecimal price() {
        return response == null || response.price() == null ? BigDecimal.ZERO : response.price();
    }
}
