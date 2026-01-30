package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.config.GoldProperties;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class GoldPriceHistory {

    private final Duration window;
    private final int capacity;
    private final GoldPriceSnapshot[] snapshots;
    private int head = 0;
    private int size = 0;

    public GoldPriceHistory(GoldProperties properties) {
        this.window = properties.getHistoryWindow();
        int configuredCapacity = properties.getHistoryCapacity();
        this.capacity = configuredCapacity > 0 ? configuredCapacity : 2000;
        this.snapshots = new GoldPriceSnapshot[this.capacity];
    }

    public synchronized void add(GoldPriceSnapshot snapshot) {
        if (size < capacity) {
            snapshots[(head + size) % capacity] = snapshot;
            size++;
        } else {
            snapshots[head] = snapshot;
            head = (head + 1) % capacity;
        }
        prune(snapshot.fetchedAt());
    }

    public synchronized Optional<GoldPriceSnapshot> findSnapshotAtOrBefore(Instant target) {
        GoldPriceSnapshot candidate = null;
        for (int i = 0; i < size; i++) {
            GoldPriceSnapshot snapshot = snapshots[(head + i) % capacity];
            if (snapshot.fetchedAt().isAfter(target)) {
                break;
            }
            candidate = snapshot;
        }
        return Optional.ofNullable(candidate);
    }

    public synchronized List<GoldPriceSnapshot> getAll() {
        List<GoldPriceSnapshot> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(snapshots[(head + i) % capacity]);
        }
        return List.copyOf(result);
    }

    public synchronized List<GoldPriceSnapshot> getRecent(int limit) {
        int safeLimit = Math.max(0, limit);
        if (safeLimit == 0 || size == 0) {
            return List.of();
        }
        int start = Math.max(0, size - safeLimit);
        int resultSize = size - start;
        List<GoldPriceSnapshot> result = new ArrayList<>(resultSize);
        for (int i = start; i < size; i++) {
            result.add(snapshots[(head + i) % capacity]);
        }
        return List.copyOf(result);
    }

    private void prune(Instant now) {
        Instant cutoff = now.minus(window);
        while (size > 0 && snapshots[head].fetchedAt().isBefore(cutoff)) {
            snapshots[head] = null;
            head = (head + 1) % capacity;
            size--;
        }
    }
}
