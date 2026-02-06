# Copilot Instructions for gold-price-alert

## Big Picture
- This is a Java 21 + Spring Boot app that polls a gold price API, stores snapshots, evaluates alerts, and sends email notifications.
- Main runtime path: `GoldPriceFetcher.fetch()` (scheduled by `gold.fetch-interval`) and `FetchPriceController` (`/price`) both call `fetchOnce()`.
- Core flow: fetch API data -> persist `gold_price_snapshot` -> evaluate volatility alerts -> persist `gold_alert_history` -> notify email service.
- Threshold flow runs in the same fetch cycle: `GoldThresholdAlertEvaluator` detects crossing events and `GoldThresholdAlertScheduler` fan-outs delayed emails (0m/1m/3m/6m/10m).
- Keep percentage thresholds in `GoldAlertLevel` and configurable windows in `gold.alert.window.levels`; both are used together by `GoldAlertEvaluator`.

## Code Boundaries
- `controller/`: HTTP endpoints only (no heavy business logic).
- `service/`: business rules (fetching, evaluation, notification, scheduling, API status monitoring).
- `repository/`: persistence operations wrapping MyBatis-Plus mappers.
- `mapper/`: thin `BaseMapper` interfaces; avoid business logic here.
- `model/`: DB-mapped entities (`@TableName/@TableField`) and API payload models.

## Data and State Conventions
- DB schema contracts are documented in `docs/gold_price_snapshot.sql`, `docs/gold_alert_history.sql`, and `docs/gold_threshold_history.sql`.
- `GoldThresholdStore` owns threshold lifecycle (`PENDING -> TRIGGERED/CLEARED`) and Redis cache key `gold:alert:threshold`.
- `GoldPriceHistory.getRecent(n)` returns newest-first order; call sites rely on index `0` being the latest snapshot.
- Time-dependent code should use injected `Clock` (existing services/tests rely on this pattern).

## Critical Behavior to Preserve
- `GoldPriceFetcher` intentionally skips weekends and London-market holidays before calling external API.
- `GoldAlertEvaluator` picks the candidate with largest absolute percent move; tie-breaker is higher alert level.
- Mail gating is strict: `gold.alert.mail.min-level` and per-level cooldowns apply, but level escalation sends immediately.
- Startup validation requires full config coverage for all alert levels in both `gold.alert.window.levels` and `gold.alert.mail.cooldowns`.

## Developer Workflows
- Run tests: `mvn test` (currently includes a `@Tag("manual")` test by default).
- Run app locally: `mvn spring-boot:run` (port `8080` from `src/main/resources/application.yml`).
- Build artifact: `mvn clean package` (Dockerfile expects `target/*.jar`).
- Build container: `docker build -t gold-price-alert .`.

## Useful Endpoints for Debugging
- `GET /price`: trigger one fetch/evaluation cycle immediately.
- `GET /history?length=100`: read recent snapshots.
- `GET|POST|DELETE /threshold`: manage active threshold.
- `GET /alert/list?pageNum=1&pageSize=20&alertLevel=P3`: query persisted alert history.
- `POST /test/email`: send a test alert email using recent snapshot history.

## Integrations and Config
- External dependencies: gold API (OkHttp), MySQL (MyBatis-Plus), Redis (`StringRedisTemplate`), SMTP (`JavaMailSender`).
- Secrets/config can be loaded via `spring.config.import=optional:file:${GOLD_SECRET_CONFIG:...}` and env vars in `application.yml`.
- Request tracing/logging is filter-based: `TraceIdFilter` + `RequestLoggingFilter`; preserve these when adding middleware.
