package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import com.xbleey.goldpricealert.repository.GoldPriceSnapshotStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class GoldPriceHistory {

    private final GoldPriceSnapshotStore store;

    public GoldPriceHistory(GoldPriceSnapshotStore store) {
        this.store = store;
    }

    public synchronized void add(GoldPriceSnapshot snapshot) {
        store.save(snapshot);
    }

    public synchronized Optional<GoldPriceSnapshot> findSnapshotAtOrBefore(Instant target) {
        return store.findSnapshotAtOrBefore(target);
    }

    public synchronized List<GoldPriceSnapshot> getAll() {
        return store.findAllAsc();
    }

    public synchronized List<GoldPriceSnapshot> getRecent(int limit) {
        int safeLimit = Math.max(0, limit);
        if (safeLimit == 0) {
            return List.of();
        }
        List<GoldPriceSnapshot> recent = store.findRecentDesc(safeLimit);
        if (recent.isEmpty()) {
            return List.of();
        }
        List<GoldPriceSnapshot> ordered = new ArrayList<>(recent);
        ordered.sort(Comparator.comparing(GoldPriceSnapshot::getFetchedAt).reversed());
        return List.copyOf(ordered);
    }
}
