package com.xbleey.goldpricealert.model;

import java.time.Instant;

public record GoldPriceSnapshot(Instant fetchedAt, GoldApiResponse response) {

    public double price() {
        return response == null ? 0.0 : response.price();
    }
}
