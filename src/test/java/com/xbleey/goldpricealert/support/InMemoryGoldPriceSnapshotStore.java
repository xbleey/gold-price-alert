package com.xbleey.goldpricealert.support;

import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import com.xbleey.goldpricealert.repository.GoldPriceSnapshotStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class InMemoryGoldPriceSnapshotStore implements GoldPriceSnapshotStore {

    private final List<GoldPriceSnapshot> snapshots = new ArrayList<>();
    private long sequence = 1;

    @Override
    public GoldPriceSnapshot save(GoldPriceSnapshot snapshot) {
        if (snapshot.getId() == null) {
            snapshot.setId(sequence++);
        }
        snapshots.add(snapshot);
        return snapshot;
    }

    @Override
    public int update(GoldPriceSnapshot snapshot) {
        if (snapshot.getId() == null) {
            return 0;
        }
        for (int i = 0; i < snapshots.size(); i++) {
            GoldPriceSnapshot current = snapshots.get(i);
            if (snapshot.getId().equals(current.getId())) {
                snapshots.set(i, snapshot);
                return 1;
            }
        }
        return 0;
    }

    @Override
    public int deleteById(Long id) {
        if (id == null) {
            return 0;
        }
        return snapshots.removeIf(snapshot -> id.equals(snapshot.getId())) ? 1 : 0;
    }

    @Override
    public Optional<GoldPriceSnapshot> findSnapshotAtOrBefore(Instant target) {
        if (target == null) {
            return Optional.empty();
        }
        return snapshots.stream()
                .filter(snapshot -> !snapshot.getFetchedAt().isAfter(target))
                .max(Comparator.comparing(GoldPriceSnapshot::getFetchedAt));
    }

    @Override
    public List<GoldPriceSnapshot> findAllAsc() {
        return snapshots.stream()
                .sorted(Comparator.comparing(GoldPriceSnapshot::getFetchedAt))
                .toList();
    }

    @Override
    public List<GoldPriceSnapshot> findRecentDesc(int limit) {
        int safeLimit = Math.max(0, limit);
        return snapshots.stream()
                .sorted(Comparator.comparing(GoldPriceSnapshot::getFetchedAt).reversed())
                .limit(safeLimit)
                .toList();
    }
}
