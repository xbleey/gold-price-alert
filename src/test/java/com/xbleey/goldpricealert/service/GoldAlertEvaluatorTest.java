package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import com.xbleey.goldpricealert.model.GoldApiResponse;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import com.xbleey.goldpricealert.support.InMemoryGoldPriceSnapshotStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GoldAlertEvaluatorTest {

    @Test
    void returnsTrueWhenChangeExceedsThreshold() {
        Instant now = Instant.parse("2026-01-05T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        GoldPriceHistory history = new GoldPriceHistory(new InMemoryGoldPriceSnapshotStore());
        GoldAlertEvaluator evaluator = new GoldAlertEvaluator(
                history,
                clock,
                GoldAlertNotifier.noop(),
                windowProperties()
        );

        history.add(snapshot(now.minus(Duration.ofMinutes(2)), "100.00"));

        GoldPriceSnapshot latest = snapshot(now, "101.00");

        assertThat(evaluator.evaluate(latest)).isTrue();
    }

    @Test
    void returnsFalseWhenNoBaselineExists() {
        Instant now = Instant.parse("2026-01-05T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        GoldPriceHistory history = new GoldPriceHistory(new InMemoryGoldPriceSnapshotStore());
        GoldAlertEvaluator evaluator = new GoldAlertEvaluator(
                history,
                clock,
                GoldAlertNotifier.noop(),
                windowProperties()
        );

        GoldPriceSnapshot latest = snapshot(now, "101.00");

        assertThat(evaluator.evaluate(latest)).isFalse();
    }

    @Test
    void prefersHigherLevelWhenAbsChangeTies() {
        Instant now = Instant.parse("2026-01-05T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        GoldPriceHistory history = new GoldPriceHistory(new InMemoryGoldPriceSnapshotStore());
        AtomicReference<GoldAlertMessage> captured = new AtomicReference<>();
        GoldAlertEvaluator evaluator = new GoldAlertEvaluator(
                history,
                clock,
                captured::set,
                windowProperties()
        );

        history.add(snapshot(now.minus(Duration.ofMinutes(60)), "100.00"));
        history.add(snapshot(now.minus(Duration.ofMinutes(15)), "101.10"));
        history.add(snapshot(now.minus(Duration.ofMinutes(5)), "101.40"));
        history.add(snapshot(now.minus(Duration.ofMinutes(1)), "101.55"));

        GoldPriceSnapshot latest = snapshot(now, "102.00");

        assertThat(evaluator.evaluate(latest)).isTrue();
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().level()).isEqualTo(GoldAlertLevel.CRITICAL_LEVEL);
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

    private static com.xbleey.goldpricealert.config.GoldAlertWindowProperties windowProperties() {
        com.xbleey.goldpricealert.config.GoldAlertWindowProperties properties =
                new com.xbleey.goldpricealert.config.GoldAlertWindowProperties();
        Map<GoldAlertLevel, Duration> levels = new EnumMap<>(GoldAlertLevel.class);
        levels.put(GoldAlertLevel.INFO_LEVEL, Duration.ofMinutes(1));
        levels.put(GoldAlertLevel.MINOR_LEVEL, Duration.ofMinutes(5));
        levels.put(GoldAlertLevel.MODERATE_LEVEL, Duration.ofMinutes(15));
        levels.put(GoldAlertLevel.MAJOR_LEVEL, Duration.ofMinutes(60));
        levels.put(GoldAlertLevel.CRITICAL_LEVEL, Duration.ofMinutes(60));
        properties.setLevels(levels);
        return properties;
    }
}
