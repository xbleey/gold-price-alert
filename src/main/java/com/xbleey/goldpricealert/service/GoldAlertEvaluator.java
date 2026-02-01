package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
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

    public GoldAlertEvaluator(GoldPriceHistory history, Clock clock, GoldAlertNotifier alertNotifier) {
        this.history = history;
        this.clock = clock;
        this.alertNotifier = alertNotifier == null ? GoldAlertNotifier.noop() : alertNotifier;
    }

    public boolean evaluate(GoldPriceSnapshot latest) {
        AlertCandidate bestCandidate = null;
        for (GoldAlertLevel level : GoldAlertLevel.values()) {
            Instant target = latest.fetchedAt().minus(level.getWindow());
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
                BigDecimal threshold = level.getThresholdPercent();
                if (absChange.compareTo(threshold) >= 0) {
                    if (bestCandidate == null || absChange.compareTo(bestCandidate.absChangePercent) > 0) {
                        bestCandidate = new AlertCandidate(level, baselinePrice, changePercent, absChange);
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
            alertNotifier.notifyAlert(new GoldAlertMessage(
                    bestCandidate.level,
                    alertMessage,
                    latest.fetchedAt(),
                    bestCandidate.level.getWindow(),
                    bestCandidate.level.getThresholdPercent(),
                    bestCandidate.changePercent,
                    bestCandidate.baselinePrice,
                    latest.price(),
                    history.getRecent(10)
            ));
            return true;
        }
        return false;
    }

    private String formatPercent(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatAlertMessage(AlertCandidate candidate, GoldPriceSnapshot latest, String alertTime) {
        return "WARNING!!WARNING!!WARNING!! level: " + candidate.level.getLevelName()
                + " | window=" + candidate.level.getWindow()
                + " threshold=" + formatPercent(candidate.level.getThresholdPercent()) + "%, change="
                + formatPercent(candidate.changePercent) + "%, price "
                + candidate.baselinePrice + " -> " + latest.price()
                + " time=" + alertTime;
    }

    private static final class AlertCandidate {
        private final GoldAlertLevel level;
        private final BigDecimal baselinePrice;
        private final BigDecimal changePercent;
        private final BigDecimal absChangePercent;

        private AlertCandidate(
                GoldAlertLevel level,
                BigDecimal baselinePrice,
                BigDecimal changePercent,
                BigDecimal absChangePercent
        ) {
            this.level = level;
            this.baselinePrice = baselinePrice;
            this.changePercent = changePercent;
            this.absChangePercent = absChangePercent;
        }
    }
}
