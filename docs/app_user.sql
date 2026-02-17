-- 系统用户表（Spring Security 认证）
CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    username VARCHAR(64) NOT NULL COMMENT '登录名（唯一）',
    password VARCHAR(255) NOT NULL COMMENT '密码（Spring Security 编码）',
    role VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用，0=禁用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
    UNIQUE KEY uk_app_user_username (username),
    INDEX idx_app_user_role_enabled (role, enabled)
) COMMENT='系统用户';

-- 执行建表后，应用启动时会自动创建 admin/admin（若 admin 不存在）。
