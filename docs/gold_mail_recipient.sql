-- 邮件收件人配置表（替代 GOLD_MAIL_RECIPIENTS 环境变量）
CREATE TABLE IF NOT EXISTS gold_mail_recipient (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    email VARCHAR(320) NOT NULL COMMENT '收件人邮箱（唯一）',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用，0=禁用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
    UNIQUE KEY uk_gold_mail_recipient_email (email),
    INDEX idx_gold_mail_recipient_enabled (enabled)
) COMMENT='告警邮件收件人配置';
