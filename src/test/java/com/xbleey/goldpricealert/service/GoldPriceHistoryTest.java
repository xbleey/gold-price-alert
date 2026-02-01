package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.config.GoldProperties;
import com.xbleey.goldpricealert.model.GoldApiResponse;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoldPriceHistoryTest {

    @Test
    void prunesSnapshotsOutsideWindow() {
        GoldPriceHistory history = new GoldPriceHistory(properties(Duration.ofMinutes(10), 10));
        Instant base = Instant.parse("2026-01-05T12:00:00Z");

        history.add(snapshot(base.minus(Duration.ofMinutes(20)), "1900.00"));
        history.add(snapshot(base, "1910.00"));

        List<GoldPriceSnapshot> all = history.getAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).fetchedAt()).isEqualTo(base);
    }

    @Test
    void respectsCapacityAndReturnsRecentSnapshots() {
        GoldPriceHistory history = new GoldPriceHistory(properties(Duration.ofMinutes(60), 2));
        Instant base = Instant.parse("2026-01-05T12:00:00Z");

        GoldPriceSnapshot s1 = snapshot(base.minusSeconds(2), "1900.00");
        GoldPriceSnapshot s2 = snapshot(base.minusSeconds(1), "1901.00");
        GoldPriceSnapshot s3 = snapshot(base, "1902.00");

        history.add(s1);
        history.add(s2);
        history.add(s3);

        assertThat(history.getAll()).containsExactly(s2, s3);
        assertThat(history.getRecent(1)).containsExactly(s3);
    }

    @Test
    void findsSnapshotAtOrBeforeTarget() {
        GoldPriceHistory history = new GoldPriceHistory(properties(Duration.ofMinutes(60), 10));
        Instant base = Instant.parse("2026-01-05T12:00:00Z");

        GoldPriceSnapshot s1 = snapshot(base.minusSeconds(20), "1900.00");
        GoldPriceSnapshot s2 = snapshot(base.minusSeconds(10), "1901.00");

        history.add(s1);
        history.add(s2);

        assertThat(history.findSnapshotAtOrBefore(base.minusSeconds(15)))
                .contains(s1);
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
