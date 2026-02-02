package com.xbleey.goldpricealert.mapper;

import com.xbleey.goldpricealert.model.GoldApiResponse;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import com.xbleey.goldpricealert.support.InMemoryGoldPriceSnapshotStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoldPriceSnapshotStoreTest {

    @Test
    void supportsCrudOperations() {
        InMemoryGoldPriceSnapshotStore store = new InMemoryGoldPriceSnapshotStore();
        Instant time1 = Instant.parse("2026-01-05T12:00:00Z");
        Instant time2 = Instant.parse("2026-01-05T12:01:00Z");

        GoldPriceSnapshot created = snapshot(time1, "1900.00");
        store.save(created);

        assertThat(created.getId()).isNotNull();
        assertThat(store.findAllAsc()).hasSize(1);

        GoldPriceSnapshot updated = snapshot(time2, "1910.00");
        updated.setId(created.getId());
        store.update(updated);

        GoldPriceSnapshot fetched = store.findSnapshotAtOrBefore(time2).orElse(null);
        assertThat(fetched).isNotNull();
        assertThat(fetched.getFetchedAt()).isEqualTo(time2);
        assertThat(fetched.price()).isEqualByComparingTo("1910.00");

        store.deleteById(created.getId());
        assertThat(store.findAllAsc()).isEmpty();
    }

    @Test
    void returnsRecentSnapshotsInDescOrderWithLimit() {
        InMemoryGoldPriceSnapshotStore store = new InMemoryGoldPriceSnapshotStore();
        Instant base = Instant.parse("2026-01-05T12:00:00Z");

        store.save(snapshot(base.minusSeconds(2), "1900.00"));
        store.save(snapshot(base.minusSeconds(1), "1901.00"));
        store.save(snapshot(base, "1902.00"));

        List<GoldPriceSnapshot> recent = store.findRecentDesc(2);

        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).getFetchedAt()).isEqualTo(base);
        assertThat(recent.get(1).getFetchedAt()).isEqualTo(base.minusSeconds(1));
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
