package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.model.GoldApiResponse;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import com.xbleey.goldpricealert.support.InMemoryGoldPriceSnapshotStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoldPriceHistoryTest {

    @Test
    void storesAllSnapshots() {
        GoldPriceHistory history = new GoldPriceHistory(new InMemoryGoldPriceSnapshotStore());
        Instant base = Instant.parse("2026-01-05T12:00:00Z");

        GoldPriceSnapshot s1 = snapshot(base.minus(Duration.ofMinutes(20)), "1900.00");
        GoldPriceSnapshot s2 = snapshot(base, "1910.00");

        history.add(s1);
        history.add(s2);

        List<GoldPriceSnapshot> all = history.getAll();
        assertThat(all).containsExactly(s1, s2);
    }

    @Test
    void returnsRecentSnapshotsInChronologicalOrder() {
        GoldPriceHistory history = new GoldPriceHistory(new InMemoryGoldPriceSnapshotStore());
        Instant base = Instant.parse("2026-01-05T12:00:00Z");

        GoldPriceSnapshot s1 = snapshot(base.minusSeconds(2), "1900.00");
        GoldPriceSnapshot s2 = snapshot(base.minusSeconds(1), "1901.00");
        GoldPriceSnapshot s3 = snapshot(base, "1902.00");

        history.add(s1);
        history.add(s2);
        history.add(s3);

        assertThat(history.getRecent(2)).containsExactly(s3, s2);
    }

    @Test
    void findsSnapshotAtOrBeforeTarget() {
        GoldPriceHistory history = new GoldPriceHistory(new InMemoryGoldPriceSnapshotStore());
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

}
