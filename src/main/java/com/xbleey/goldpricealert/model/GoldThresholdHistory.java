package com.xbleey.goldpricealert.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@TableName("gold_threshold_history")
public class GoldThresholdHistory {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_TRIGGERED = "TRIGGERED";
    public static final String STATUS_CLEARED = "CLEARED";

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("threshold")
    private BigDecimal threshold;

    @TableField("set_at")
    private Instant setAt;

    @TableField("triggered_at")
    private Instant triggeredAt;

    @TableField("triggered_price")
    private BigDecimal triggeredPrice;

    @TableField("status")
    private String status;

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }
}
