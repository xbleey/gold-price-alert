package com.xbleey.goldpricealert.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class GoldApiResponse {

    private String name;

    private BigDecimal price;

    private String symbol;

    private Instant updatedAt;

    private String updatedAtReadable;

    @JsonCreator
    public GoldApiResponse(
            @JsonProperty("name") String name,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("updatedAt") Instant updatedAt,
            @JsonProperty("updatedAtReadable") String updatedAtReadable
    ) {
        this.name = name;
        this.price = price;
        this.symbol = symbol;
        this.updatedAt = updatedAt;
        this.updatedAtReadable = updatedAtReadable;
    }

    public String name() {
        return name;
    }

    public BigDecimal price() {
        return price;
    }

    public String symbol() {
        return symbol;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String updatedAtReadable() {
        return updatedAtReadable;
    }
}
