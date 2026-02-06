-- 金价波动告警历史记录表（记录所有等级，含 P1/P2）
CREATE TABLE IF NOT EXISTS gold_alert_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    alert_level VARCHAR(8) NOT NULL COMMENT '告警等级：P1/P2/P3/P4/P5',
    alert_time_utc TIMESTAMP NOT NULL COMMENT '告警时间（UTC）',
    alert_time_beijing DATETIME NOT NULL COMMENT '告警时间（北京时间，UTC+8）',
    threshold_percent DECIMAL(10, 4) NOT NULL COMMENT '阈值（百分比）',
    change_percent DECIMAL(10, 4) NOT NULL COMMENT '变化值（百分比）',
    baseline_price DECIMAL(18, 4) NOT NULL COMMENT '报警前金价',
    latest_price DECIMAL(18, 4) NOT NULL COMMENT '报警当前金价',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间（UTC）',
    INDEX idx_gold_alert_history_time (alert_time_utc),
    INDEX idx_gold_alert_history_level_time (alert_level, alert_time_utc)
) COMMENT='金价波动告警历史';
