package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.config.GoldProperties;
import com.xbleey.goldpricealert.model.GoldApiResponse;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GoldAlertEvaluatorTest {

    @Test
    void returnsTrueWhenChangeExceedsThreshold() {
        Instant now = Instant.parse("2026-01-05T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        GoldPriceHistory history = new GoldPriceHistory(properties(Duration.ofMinutes(120), 100));
        GoldAlertEvaluator evaluator = new GoldAlertEvaluator(history, clock, GoldAlertNotifier.noop());

        history.add(snapshot(now.minus(Duration.ofMinutes(2)), "100.00"));

        GoldPriceSnapshot latest = snapshot(now, "101.00");

        assertThat(evaluator.evaluate(latest)).isTrue();
    }

    @Test
    void returnsFalseWhenNoBaselineExists() {
        Instant now = Instant.parse("2026-01-05T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        GoldPriceHistory history = new GoldPriceHistory(properties(Duration.ofMinutes(120), 100));
        GoldAlertEvaluator evaluator = new GoldAlertEvaluator(history, clock, GoldAlertNotifier.noop());

        GoldPriceSnapshot latest = snapshot(now, "101.00");

        assertThat(evaluator.evaluate(latest)).isFalse();
    }

    private static GoldPriceSnapshot snapshot(Instant time, String price) {
        GoldApiResponse response = new GoldApiResponse(
                "gold",
                new BigDecimal(price),
                "XAU",
                time,
                time.toString()
        );
        return new GoldPriceSnapshot(time, response);
    }

    private static GoldProperties properties(Duration window, int capacity) {
        GoldProperties properties = new GoldProperties();
        properties.setHistoryWindow(window);
        properties.setHistoryCapacity(capacity);
        return properties;
    }
}
