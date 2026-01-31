# gold-price-alert

一个基于 Spring Boot 的金价监控小服务，定时从 gold-api.com 拉取最新金价，并根据波动幅度输出告警日志。

## 目标
1. 读取 `gold-api.com` 的 API 获取最新金价，每 20s 请求一次。
2. 以 `GoldAlertLevel` 为标准，设置 5 个等级。
3. 记录一小时内请求记录。

## 告警等级（GoldAlertLevel）
| 等级 | 观察窗口  | 触发阈值（绝对涨跌幅） |
| --- |-------|-------------|
| INFO_LEVEL | 1 分钟  | 0.08%       |
| MINOR_LEVEL | 5 分钟  | 0.20%       |
| MODERATE_LEVEL | 15 分钟 | 0.45%       |
| MAJOR_LEVEL | 60 分钟 | 0.90%       |
| CRITICAL_LEVEL | 60 分钟 | 1.50%       |

## 运行与配置
核心配置在 `src/main/resources/application.yml`：

```yaml
gold:
  api-url: https://api.gold-api.com/price/XAU
  fetch-interval: 1m
  history-window: 1h
```

- `fetch-interval`: 拉取频率（当前为 1m）。
- `history-window`: 历史记录保留窗口（当前为 1h）。

## 运行方式
- 直接运行 Spring Boot 应用即可（默认端口 80）。
- 定时任务会自动按 `fetch-interval` 周期拉取并写入历史记录，同时触发告警评估。
