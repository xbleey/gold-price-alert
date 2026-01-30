package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class GoldAlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(GoldAlertEvaluator.class);

    private final GoldPriceHistory history;

    public GoldAlertEvaluator(GoldPriceHistory history) {
        this.history = history;
    }

    public void evaluate(GoldPriceSnapshot latest) {
        for (GoldAlertLevel level : GoldAlertLevel.values()) {
            Instant target = latest.fetchedAt().minus(level.getWindow());
            Optional<GoldPriceSnapshot> baseline = history.findSnapshotAtOrBefore(target);
            if (baseline.isPresent()) {
                double baselinePrice = baseline.get().price();
                if (baselinePrice > 0.0) {
                    double changePercent = ((latest.price() - baselinePrice) / baselinePrice) * 100.0;
                    double absChange = Math.abs(changePercent);
                    if (absChange >= level.getThresholdPercent() && log.isWarnEnabled()) {
                        log.warn(
                                "ALERT window={} threshold={}%, change={}%, price {} -> {}",
                                level.getWindow(),
                                formatPercent(level.getThresholdPercent()),
                                formatPercent(changePercent),
                                baselinePrice,
                                latest.price()
                        );
                    }
                }
            }
        }
    }

    private String formatPercent(double value) {
        return String.format(Locale.US, "%.4f", value);
    }
}
