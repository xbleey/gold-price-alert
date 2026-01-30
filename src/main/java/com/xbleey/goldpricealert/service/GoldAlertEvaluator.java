package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Service
public class GoldAlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(GoldAlertEvaluator.class);

    private final GoldPriceHistory history;
    private final Clock clock;

    public GoldAlertEvaluator(GoldPriceHistory history, Clock clock) {
        this.history = history;
        this.clock = clock;
    }

    public boolean evaluate(GoldPriceSnapshot latest) {
        AlertCandidate bestCandidate = null;
        for (GoldAlertLevel level : GoldAlertLevel.values()) {
            Instant target = latest.fetchedAt().minus(level.getWindow());
            Optional<GoldPriceSnapshot> baseline = history.findSnapshotAtOrBefore(target);
            if (baseline.isPresent()) {
                double baselinePrice = baseline.get().price();
                if (baselinePrice > 0.0) {
                    double changePercent = ((latest.price() - baselinePrice) / baselinePrice) * 100.0;
                    double absChange = Math.abs(changePercent);
                    if (absChange >= level.getThresholdPercent()) {
                        if (bestCandidate == null || absChange > bestCandidate.absChangePercent) {
                            bestCandidate = new AlertCandidate(level, baselinePrice, changePercent, absChange);
                        }
                    }
                }
            }
        }
        if (bestCandidate != null && log.isWarnEnabled()) {
            String alertTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(clock.getZone())
                    .format(latest.fetchedAt());
            log.warn(
                    "ALERT!! level: {} | window={} threshold={}%, change={}%, price {} -> {} time={}",
                    bestCandidate.level.getLevelName(),
                    bestCandidate.level.getWindow(),
                    formatPercent(bestCandidate.level.getThresholdPercent()),
                    formatPercent(bestCandidate.changePercent),
                    bestCandidate.baselinePrice,
                    latest.price(),
                    alertTime
            );
            return true;
        }
        return false;
    }

    private String formatPercent(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private static final class AlertCandidate {
        private final GoldAlertLevel level;
        private final double baselinePrice;
        private final double changePercent;
        private final double absChangePercent;

        private AlertCandidate(
                GoldAlertLevel level,
                double baselinePrice,
                double changePercent,
                double absChangePercent
        ) {
            this.level = level;
            this.baselinePrice = baselinePrice;
            this.changePercent = changePercent;
            this.absChangePercent = absChangePercent;
        }
    }
}
