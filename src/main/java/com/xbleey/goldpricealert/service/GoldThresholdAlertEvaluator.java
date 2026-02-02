package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.enums.GoldThresholdDirection;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class GoldThresholdAlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(GoldThresholdAlertEvaluator.class);

    private final GoldPriceHistory history;
    private final GoldThresholdStore thresholdStore;
    private final GoldThresholdAlertScheduler scheduler;

    public GoldThresholdAlertEvaluator(
            GoldPriceHistory history,
            GoldThresholdStore thresholdStore,
            GoldThresholdAlertScheduler scheduler
    ) {
        this.history = history;
        this.thresholdStore = thresholdStore;
        this.scheduler = scheduler;
    }

    public boolean evaluate(GoldPriceSnapshot latest) {
        if (latest == null) {
            return false;
        }
        Optional<BigDecimal> thresholdOpt = thresholdStore.getThreshold();
        if (thresholdOpt.isEmpty()) {
            return false;
        }
        BigDecimal threshold = thresholdOpt.get();
        if (threshold.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        List<GoldPriceSnapshot> recent = history.getRecent(2);
        if (recent.size() < 2) {
            return false;
        }
        GoldPriceSnapshot previous = recent.get(1);
        GoldThresholdDirection direction = resolveDirection(previous, latest, threshold);
        if (direction == null) {
            return false;
        }
        GoldThresholdAlertMessage message = new GoldThresholdAlertMessage(
                threshold,
                latest.price(),
                direction,
                latest.fetchedAt(),
                history.getRecent(20)
        );
        scheduler.schedule(message);
        if (log.isWarnEnabled()) {
            log.warn("Threshold alert triggered: {} {}", direction, threshold.toPlainString());
        }
        return true;
    }

    private GoldThresholdDirection resolveDirection(
            GoldPriceSnapshot previous,
            GoldPriceSnapshot latest,
            BigDecimal threshold
    ) {
        if (previous == null || latest == null || threshold == null) {
            return null;
        }
        BigDecimal previousPrice = previous.price();
        BigDecimal latestPrice = latest.price();
        int prevCmp = previousPrice.compareTo(threshold);
        int latestCmp = latestPrice.compareTo(threshold);
        if (prevCmp >= 0 && latestCmp < 0) {
            return GoldThresholdDirection.DOWN;
        }
        if (prevCmp < 0 && latestCmp >= 0) {
            return GoldThresholdDirection.UP;
        }
        return null;
    }
}
