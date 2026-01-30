package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.config.GoldProperties;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GoldPriceHistory {

    private final Duration window;
    private final Deque<GoldPriceSnapshot> snapshots = new ArrayDeque<>();

    public GoldPriceHistory(GoldProperties properties) {
        this.window = properties.getHistoryWindow();
    }

    public synchronized void add(GoldPriceSnapshot snapshot) {
        snapshots.addLast(snapshot);
        prune(snapshot.fetchedAt());
    }

    public synchronized Optional<GoldPriceSnapshot> findSnapshotAtOrBefore(Instant target) {
        GoldPriceSnapshot candidate = null;
        for (GoldPriceSnapshot snapshot : snapshots) {
            if (snapshot.fetchedAt().isAfter(target)) {
                break;
            }
            candidate = snapshot;
        }
        return Optional.ofNullable(candidate);
    }

    public synchronized List<GoldPriceSnapshot> getAll() {
        return List.copyOf(snapshots);
    }

    private void prune(Instant now) {
        Instant cutoff = now.minus(window);
        while (!snapshots.isEmpty() && snapshots.peekFirst().fetchedAt().isBefore(cutoff)) {
            snapshots.removeFirst();
        }
    }
}
