package com.xbleey.goldpricealert.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.xbleey.goldpricealert.model.GoldThresholdHistory;
import com.xbleey.goldpricealert.repository.GoldThresholdHistoryStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class GoldThresholdStore {

    private static final Logger log = LoggerFactory.getLogger(GoldThresholdStore.class);
    private static final String THRESHOLD_KEY = "gold:alert:threshold";

    private final StringRedisTemplate redisTemplate;
    private final GoldThresholdHistoryStore historyStore;
    private final Clock clock;

    public GoldThresholdStore(StringRedisTemplate redisTemplate, GoldThresholdHistoryStore historyStore, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.historyStore = historyStore;
        this.clock = clock;
    }

    public Optional<BigDecimal> getThreshold() {
        return getActiveRecord().map(GoldThresholdHistory::getThreshold);
    }

    public Optional<GoldThresholdHistory> getActiveRecord() {
        Optional<GoldThresholdHistory> record = historyStore.findLatestPending();
        if (record.isPresent()) {
            cacheThreshold(record.get().getThreshold());
        } else {
            clearCache();
        }
        return record;
    }

    public BigDecimal setThreshold(BigDecimal threshold) {
        if (threshold == null) {
            throw new IllegalArgumentException("threshold must not be null");
        }
        Instant now = clock.instant();
        Optional<GoldThresholdHistory> latest = historyStore.findLatest();
        GoldThresholdHistory record = latest.filter(GoldThresholdHistory::isPending)
                .orElseGet(GoldThresholdHistory::new);
        record.setThreshold(threshold);
        record.setSetAt(now);
        record.setStatus(GoldThresholdHistory.STATUS_PENDING);
        record.setTriggeredAt(null);
        record.setTriggeredPrice(null);
        if (record.getId() == null) {
            historyStore.save(record);
        } else {
            historyStore.update(record);
        }
        cacheThreshold(threshold);
        return threshold;
    }

    public boolean markTriggered(Instant triggeredAt, BigDecimal triggeredPrice) {
        Optional<GoldThresholdHistory> active = historyStore.findLatestPending();
        if (active.isEmpty()) {
            return false;
        }
        boolean updated = historyStore.markTriggered(active.get().getId(), triggeredAt, triggeredPrice);
        if (updated) {
            clearCache();
        }
        return updated;
    }

    public void clearThreshold() {
        Optional<GoldThresholdHistory> active = historyStore.findLatestPending();
        active.ifPresent(record -> historyStore.markCleared(record.getId()));
        clearCache();
    }

    private void cacheThreshold(BigDecimal threshold) {
        if (threshold == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(THRESHOLD_KEY, threshold.toPlainString());
        } catch (Exception ex) {
            log.warn("Failed to cache threshold to redis", ex);
        }
    }

    private void clearCache() {
        try {
            redisTemplate.delete(THRESHOLD_KEY);
        } catch (Exception ex) {
            log.warn("Failed to clear threshold from redis", ex);
        }
    }
}
