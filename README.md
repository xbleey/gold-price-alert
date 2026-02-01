# gold-price-alert

一个基于 Spring Boot 的金价监控小服务，定时从 gold-api.com 拉取最新金价，并按波动幅度触发告警与邮件通知。

## 核心流程
1. 按 `fetch-interval` 拉取最新价格并入库（内存历史队列）。
2. 对每个告警等级，取“该等级窗口对应时间点”之前的最近快照作为基准价。
3. 计算涨跌幅（百分比），取绝对值与阈值比较。
4. 若多个等级同时满足，选择“绝对涨跌幅最大”的等级作为本次告警。
5. 邮件发送受 `min-level` 与 per-level 冷却时间控制；若等级升级则立即发送。

## 告警等级（GoldAlertLevel）
| 等级 | 观察窗口 | 触发阈值（绝对涨跌幅） |
| --- | --- | --- |
| INFO_LEVEL | 1 分钟 | 0.08% |
| MINOR_LEVEL | 5 分钟 | 0.30% |
| MODERATE_LEVEL | 15 分钟 | 0.60% |
| MAJOR_LEVEL | 60 分钟 | 1.00% |
| CRITICAL_LEVEL | 60 分钟 | 1.60% |

## 告警机制示例（时间线表格）
假设“当前时间”为 10:15，当前价格 4,888。基于各等级窗口回溯得到如下基准价与涨跌幅：

| 时间点 | 价格 | 涨跌幅（相对 10:15） | 触发等级 |
| --- | --- | --- | --- |
| 10:15 | 4,888 | 0.00% | - |
| 10:14 (1m) | 4,884 | +0.08% | INFO_LEVEL |
| 10:10 (5m) | 4,870 | +0.37% | MINOR_LEVEL |
| 10:00 (15m) | 4,858 | +0.62% | MODERATE_LEVEL |
| 09:15 (60m) | 4,845 | +0.89% | 未达 MAJOR |

说明：
- 涨跌幅计算公式：`(当前价 - 基准价) / 基准价 * 100%`，取绝对值与阈值比较。
- 若多个等级同时满足，系统会选“绝对涨跌幅最大”的等级作为本次告警（此例为 MODERATE_LEVEL）。
- 邮件发送还会受每个等级的冷却时间限制；若等级升级则立即发送。

## 运行与配置
核心配置在 `src/main/resources/application.yml`：

```yaml
gold:
  api-url: https://api.gold-api.com/price/XAU
  fetch-interval: 1m
  history-window: 1h
  history-capacity: 2000
  log-dir: ${GOLD_LOG_DIR:/app/logs}
  alert:
    mail:
      sender: ${GOLD_MAIL_SENDER:}
      recipients: ${GOLD_MAIL_RECIPIENTS:}
      min-level: ${GOLD_MAIL_MIN_LEVEL:MINOR_LEVEL}
      cooldowns:
        INFO_LEVEL: ${GOLD_MAIL_COOLDOWN_INFO:30m}
        MINOR_LEVEL: ${GOLD_MAIL_COOLDOWN_MINOR:15m}
        MODERATE_LEVEL: ${GOLD_MAIL_COOLDOWN_MODERATE:10m}
        MAJOR_LEVEL: ${GOLD_MAIL_COOLDOWN_MAJOR:5m}
        CRITICAL_LEVEL: ${GOLD_MAIL_COOLDOWN_CRITICAL:0m}
```

说明：
- `history-window` 建议不小于最大告警窗口（当前为 60m）。
- `gold.alert.mail.cooldowns.<LEVEL>` 必须为所有等级配置，否则启动报错。
- 冷却时间支持 `0m`，表示不限制频率。

## 运行方式
- 直接运行 Spring Boot 应用即可（默认端口 80）。
- 定时任务会自动按 `fetch-interval` 拉取并评估告警。
