package com.xbleey.goldpricealert.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@TableName("gold_price_snapshot")
public class GoldPriceSnapshot {

    @JsonIgnore
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("fetched_at")
    private Instant fetchedAt;

    @TableField("name")
    private String name;

    @TableField("price")
    private BigDecimal price;

    @TableField("symbol")
    private String symbol;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("updated_at_readable")
    private String updatedAtReadable;

    @TableField(exist = false)
    private GoldApiResponse response;

    public GoldPriceSnapshot(Instant fetchedAt, GoldApiResponse response) {
        this.fetchedAt = fetchedAt;
        setResponse(response);
    }

    public Instant fetchedAt() {
        return fetchedAt;
    }

    public GoldApiResponse response() {
        return new GoldApiResponse(
                name,
                price,
                symbol,
                updatedAt,
                updatedAtReadable
        );
    }

    public GoldApiResponse getResponse() {
        return response();
    }

    public void setResponse(GoldApiResponse response) {
        this.response = response;
        if (response == null) {
            this.name = null;
            this.price = null;
            this.symbol = null;
            this.updatedAt = null;
            this.updatedAtReadable = null;
            return;
        }
        this.name = response.name();
        this.price = response.price();
        this.symbol = response.symbol();
        this.updatedAt = response.updatedAt();
        this.updatedAtReadable = response.updatedAtReadable();
    }

    public BigDecimal price() {
        return price == null ? BigDecimal.ZERO : price;
    }
}
