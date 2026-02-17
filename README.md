# gold-price-alert

一个基于 Spring Boot 的金价监控小服务，定时从 gold-api.com 拉取最新金价，并按波动幅度触发告警与邮件通知。

## 核心流程
1. 按 `fetch-interval` 拉取最新价格并入库（MySQL，永久存储）。
2. 对每个告警等级，取“该等级窗口对应时间点”之前的最近快照作为基准价。
3. 计算涨跌幅（百分比），取绝对值与阈值比较。
4. 若多个等级同时满足，选择“绝对涨跌幅最大”的等级作为本次告警。
5. 邮件发送受 `min-level` 与 Redis 中 per-level 冷却时间控制；若等级升级则立即发送。

## 健康探针接口
- `GET /health/live`：存活探针，返回应用进程是否存活（`200` + `status=UP`）。
- `GET /health/ready`：就绪探针，检查数据库与 Redis 连通性。
  - 检查通过返回 `200` + `status=UP`。
  - 任一依赖不可用返回 `503` + `status=DOWN`。
- `GET /health`：等价于 `GET /health/ready`，方便通用监控直接接入。

## API 认证（Spring Security）
- 所有 API 均要求认证（HTTP Basic）。
- 用户管理接口 `/users/**` 仅 `ADMIN` 角色可访问。
- 用户表结构见 `docs/app_user.sql`。
- 首次启动时若 `app_user` 表中不存在 `admin` 用户，系统会自动创建默认账号：
  - 用户名：`admin`
  - 密码：`admin`

## 告警等级（GoldAlertLevel）
告警等级与 cooldown 配置统一持久化在 Redis（key: `gold:alert:levels:config`）。
- 首次读取若 Redis 无数据，系统会用内置 P1~P5 默认值初始化并写回 Redis。
- 后续读取只使用 Redis 配置，不再直接使用枚举默认值。

| 等级 | 观察窗口 | 触发阈值（绝对涨跌幅） |
| --- | --- | --- |
| INFO_LEVEL | 1 分钟 | 0.10% |
| MINOR_LEVEL | 5 分钟 | 0.30% |
| MODERATE_LEVEL | 15 分钟 | 0.60% |
| MAJOR_LEVEL | 60 分钟 | 1.00% |
| CRITICAL_LEVEL | 60 分钟 | 2.00% |

## 告警机制示例（时间线表格）
假设“当前时间”为 10:15，当前价格 4,888。基于各等级窗口回溯得到如下基准价与涨跌幅：

| 时间点（窗口） | 基准价 | 当前价 | 涨跌幅（相对基准价） | 触发等级 |
| --- | --- | --- | --- | --- |
| 10:15 | 4,888 | 4,888 | 0.00% | - |
| 10:14 (1m) | 4,883 | 4,888 | +0.10% | INFO_LEVEL |
| 10:10 (5m) | 4,870 | 4,888 | +0.37% | MINOR_LEVEL |
| 10:00 (15m) | 4,858 | 4,888 | +0.62% | MODERATE_LEVEL |
| 09:15 (60m) | 4,792 | 4,888 | +2.00% | MAJOR_LEVEL |
| 09:15 (60m) | 4,792 | 4,888 | +2.00% | CRITICAL_LEVEL |

说明：
- 涨跌幅计算公式：`(当前价 - 基准价) / 基准价 * 100%`，取绝对值与阈值比较。
- MAJOR_LEVEL 与 CRITICAL_LEVEL 共享 60 分钟窗口，因此示例中基准价与涨跌幅相同。
- 若多个等级同时满足，系统会选“绝对涨跌幅最大”的等级作为本次告警（此例为 CRITICAL_LEVEL）。
- 邮件发送还会受每个等级的冷却时间限制；若等级升级则立即发送。

## 运行与配置
核心配置在 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${GOLD_DB_HOST:10.140.0.2}:${GOLD_DB_PORT:3306}/${GOLD_DB_NAME:alert}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: ${GOLD_DB_USERNAME:root}
    password: ${GOLD_DB_PASSWORD:}

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true

gold:
  api-url: https://api.gold-api.com/price/XAU
  fetch-interval: 1m
  log-dir: ${GOLD_LOG_DIR:/app/logs}
  alert:
    mail:
      sender: ${GOLD_MAIL_SENDER:}
      min-level: ${GOLD_MAIL_MIN_LEVEL:MINOR_LEVEL}
      cooldowns:
        INFO_LEVEL: ${GOLD_MAIL_COOLDOWN_INFO:30m}
        MINOR_LEVEL: ${GOLD_MAIL_COOLDOWN_MINOR:15m}
        MODERATE_LEVEL: ${GOLD_MAIL_COOLDOWN_MODERATE:10m}
        MAJOR_LEVEL: ${GOLD_MAIL_COOLDOWN_MAJOR:5m}
        CRITICAL_LEVEL: ${GOLD_MAIL_COOLDOWN_CRITICAL:0m}
```

说明：
- `gold.alert.mail.min-level` 仍用于最低发送等级控制（支持 `MODERATE_LEVEL` 或 `P3` 形式）。
- 冷却时间改为读取 Redis 的告警等级配置中的 `cooldown` 字段。

## 告警等级配置接口（Redis 持久化）
- `GET /alert/levels`：查询全部等级配置
- `GET /alert/levels/{levelName}`：按等级查询（如 `P3`）
- `POST /alert/levels`：新增等级（仅允许 `P6` 及以上）
- `PUT /alert/levels/{levelName}`：更新等级（`P1~P5` 与 `P6+` 均可）
- `DELETE /alert/levels/{levelName}`：删除等级（仅允许 `P6` 及以上）

规则：
- `P1~P5` 为固定内置等级，不允许删除，也不允许修改等级号/等级名；允许修改 `window`、`thresholdPercent`、`cooldown`。
- `thresholdPercent` 入参必须在 `0~10` 且最多两位小数（数值按百分比语义存储与计算，例如 `5.25` 即 `5.25%`）。
- `window` 入参必须为非负整数（分钟）。

## 邮件收件人配置（MySQL 持久化）
- 表结构见 `docs/gold_mail_recipient.sql`。
- 接口：
  - `GET /mail/recipients`：查询全部收件人
  - `GET /mail/recipients/{id}`：按 id 查询
  - `POST /mail/recipients`：新增（JSON：`email`、`enabled`）
  - `PUT /mail/recipients/{id}`：修改（JSON：`email`、`enabled`）
  - `DELETE /mail/recipients/{id}`：删除
- 新增和修改时会校验邮箱正则格式，不合法会返回 `400`。

## 用户管理（MySQL 持久化）
- 接口：
  - `GET /users`：查询全部用户
  - `GET /users/{id}`：按 id 查询
  - `POST /users`：新增用户（JSON：`username`、`password`、`role`、`enabled`）
  - `PUT /users/{id}`：修改用户（JSON：`username`、`password`、`role`、`enabled`，均可选）
  - `DELETE /users/{id}`：删除用户
- 接口响应不会返回密码字段。

## 运行方式
- 直接运行 Spring Boot 应用即可（默认端口 8080）。
- 定时任务会自动按 `fetch-interval` 拉取并评估告警。
