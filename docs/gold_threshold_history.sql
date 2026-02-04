-- 金价阈值触发历史记录表
CREATE TABLE IF NOT EXISTS gold_threshold_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    threshold DECIMAL(18, 4) NOT NULL COMMENT '阈值',
    set_at TIMESTAMP NOT NULL COMMENT '阈值设置时间',
    triggered_at TIMESTAMP NULL COMMENT '触发时间',
    triggered_price DECIMAL(18, 4) NULL COMMENT '触发时金价',
    status VARCHAR(32) NOT NULL COMMENT '状态：PENDING/ TRIGGERED/ CLEARED',
    INDEX idx_gold_threshold_history_status_set_at (status, set_at)
) COMMENT='金价阈值触发历史';
