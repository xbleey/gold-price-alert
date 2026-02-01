package com.xbleey.goldpricealert.repository;

import com.xbleey.goldpricealert.model.GoldPriceSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GoldPriceSnapshotStore {

    GoldPriceSnapshot save(GoldPriceSnapshot snapshot);

    int update(GoldPriceSnapshot snapshot);

    int deleteById(Long id);

    Optional<GoldPriceSnapshot> findSnapshotAtOrBefore(Instant target);

    List<GoldPriceSnapshot> findAllAsc();

    List<GoldPriceSnapshot> findRecentDesc(int limit);
}
