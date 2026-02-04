package com.xbleey.goldpricealert.repository;

import com.xbleey.goldpricealert.model.GoldThresholdHistory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface GoldThresholdHistoryStore {

    GoldThresholdHistory save(GoldThresholdHistory record);

    int update(GoldThresholdHistory record);

    Optional<GoldThresholdHistory> findLatest();

    Optional<GoldThresholdHistory> findLatestPending();

    boolean markTriggered(Long id, Instant triggeredAt, BigDecimal triggeredPrice);

    boolean markCleared(Long id);
}
