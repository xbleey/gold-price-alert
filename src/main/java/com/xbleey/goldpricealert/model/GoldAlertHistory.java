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
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("gold_alert_history")
public class GoldAlertHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("alert_level")
    private String alertLevel;

    @TableField("alert_time_utc")
    private Instant alertTimeUtc;

    @TableField("alert_time_beijing")
    private LocalDateTime alertTimeBeijing;

    @TableField("threshold_percent")
    private BigDecimal thresholdPercent;

    @TableField("change_percent")
    private BigDecimal changePercent;

    @TableField("baseline_price")
    private BigDecimal baselinePrice;

    @TableField("latest_price")
    private BigDecimal latestPrice;

    @TableField("created_at")
    private Instant createdAt;
}
