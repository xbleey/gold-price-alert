package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.model.GoldAlertHistory;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import com.xbleey.goldpricealert.repository.GoldAlertHistoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class GoldAlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(GoldAlertEvaluator.class);
    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final GoldPriceHistory history;
    private final Clock clock;
    private final GoldAlertNotifier alertNotifier;
    private final GoldAlertLevelConfigStore configStore;
    private final GoldAlertHistoryStore alertHistoryStore;

    public GoldAlertEvaluator(
            GoldPriceHistory history,
            Clock clock,
            GoldAlertNotifier alertNotifier,
            GoldAlertLevelConfigStore configStore,
            GoldAlertHistoryStore alertHistoryStore
    ) {
        this.history = history;
        this.clock = clock;
        this.alertNotifier = alertNotifier == null ? GoldAlertNotifier.noop() : alertNotifier;
        this.configStore = configStore;
        this.alertHistoryStore = alertHistoryStore == null ? GoldAlertHistoryStore.noop() : alertHistoryStore;
    }

    public boolean evaluate(GoldPriceSnapshot latest) {
        AlertCandidate bestCandidate = null;
        for (GoldAlertLevelConfig config : configStore.listLevels()) {
            Duration window = config.windowDuration();
            Instant target = latest.fetchedAt().minus(window);
            Optional<GoldPriceSnapshot> baseline = history.findSnapshotAtOrBefore(target);
            if (baseline.isPresent()) {
                BigDecimal baselinePrice = baseline.get().price();
                if (baselinePrice.compareTo(ZERO) <= 0) {
                    continue;
                }
                BigDecimal latestPrice = latest.price();
                BigDecimal changePercent = latestPrice
                        .subtract(baselinePrice, MATH_CONTEXT)
                        .divide(baselinePrice, MATH_CONTEXT)
                        .multiply(ONE_HUNDRED, MATH_CONTEXT);
                BigDecimal absChange = changePercent.abs();
                BigDecimal threshold = config.thresholdPercent();
                if (absChange.compareTo(threshold) >= 0) {
                    if (bestCandidate == null) {
                        bestCandidate = new AlertCandidate(config, window, baselinePrice, changePercent, absChange);
                    } else {
                        int absCompare = absChange.compareTo(bestCandidate.absChangePercent());
                        if (absCompare > 0
                                || (absCompare == 0 && config.levelRank() > bestCandidate.config().levelRank())) {
                            bestCandidate = new AlertCandidate(config, window, baselinePrice, changePercent, absChange);
                        }
                    }
                }
            }
        }
        if (bestCandidate != null) {
            String alertTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(clock.getZone())
                    .format(latest.fetchedAt());
            String alertMessage = formatAlertMessage(bestCandidate, latest, alertTime);
            if (log.isWarnEnabled()) {
                log.warn(alertMessage);
            }
            GoldAlertMessage message = new GoldAlertMessage(
                    bestCandidate.config().levelName(),
                    bestCandidate.config().levelRank(),
                    alertMessage,
                    latest.fetchedAt(),
                    bestCandidate.window(),
                    bestCandidate.config().thresholdPercent(),
                    bestCandidate.changePercent(),
                    bestCandidate.baselinePrice(),
                    latest.price(),
                    history.getRecent(60)
            );
            persistAlertHistory(message);
            alertNotifier.notifyAlert(message);
            return true;
        }
        return false;
    }

    private void persistAlertHistory(GoldAlertMessage message) {
        if (message == null) {
            return;
        }
        Instant alertTime = message.alertTime() == null ? Instant.now(clock) : message.alertTime();
        GoldAlertHistory record = new GoldAlertHistory();
        record.setAlertLevel(message.levelName());
        record.setAlertTimeUtc(alertTime);
        record.setAlertTimeBeijing(LocalDateTime.ofInstant(alertTime, ZoneId.of("Asia/Shanghai")));
        record.setThresholdPercent(message.thresholdPercent());
        record.setChangePercent(message.changePercent());
        record.setBaselinePrice(message.baselinePrice());
        record.setLatestPrice(message.latestPrice());
        try {
            alertHistoryStore.save(record);
        } catch (Exception ex) {
            log.warn("Failed to persist gold alert history", ex);
        }
    }

    private String formatPercent(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatAlertMessage(AlertCandidate candidate, GoldPriceSnapshot latest, String alertTime) {
        return "WARNING!!WARNING!!WARNING!! level: " + candidate.config().levelName()
                + " | window=" + candidate.window()
                + " threshold=" + formatPercent(candidate.config().thresholdPercent()) + "%, change="
                + formatPercent(candidate.changePercent()) + "%, price "
                + candidate.baselinePrice() + " -> " + latest.price()
                + " time=" + alertTime;
    }

    private record AlertCandidate(
            GoldAlertLevelConfig config,
            Duration window,
            BigDecimal baselinePrice,
            BigDecimal changePercent,
            BigDecimal absChangePercent
    ) {
    }
}
