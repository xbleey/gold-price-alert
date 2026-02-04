package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.model.GoldThresholdHistory;
import com.xbleey.goldpricealert.repository.GoldThresholdHistoryStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GoldThresholdStoreTest {

    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void getThresholdReturnsEmptyWhenNoPendingRecord() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        GoldThresholdHistoryStore historyStore = mock(GoldThresholdHistoryStore.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(historyStore.findLatestPending()).thenReturn(Optional.empty());

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate, historyStore, clock);

        assertThat(store.getThreshold()).isEmpty();
        verify(redisTemplate).delete("gold:alert:threshold");
    }

    @Test
    void setThresholdCreatesNewWhenNoLatestRecord() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        GoldThresholdHistoryStore historyStore = mock(GoldThresholdHistoryStore.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(historyStore.findLatest()).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(ops);

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate, historyStore, clock);

        store.setThreshold(new BigDecimal("4500"));

        ArgumentCaptor<GoldThresholdHistory> captor = ArgumentCaptor.forClass(GoldThresholdHistory.class);
        verify(historyStore).save(captor.capture());
        GoldThresholdHistory saved = captor.getValue();
        assertThat(saved.getThreshold()).isEqualByComparingTo("4500");
        assertThat(saved.getSetAt()).isEqualTo(NOW);
        assertThat(saved.getStatus()).isEqualTo(GoldThresholdHistory.STATUS_PENDING);
        verify(ops).set("gold:alert:threshold", "4500");
    }

    @Test
    void setThresholdUpdatesPendingRecord() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        GoldThresholdHistoryStore historyStore = mock(GoldThresholdHistoryStore.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        GoldThresholdHistory existing = new GoldThresholdHistory();
        existing.setId(1L);
        existing.setStatus(GoldThresholdHistory.STATUS_PENDING);
        when(historyStore.findLatest()).thenReturn(Optional.of(existing));
        when(redisTemplate.opsForValue()).thenReturn(ops);

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate, historyStore, clock);

        store.setThreshold(new BigDecimal("4600"));

        ArgumentCaptor<GoldThresholdHistory> captor = ArgumentCaptor.forClass(GoldThresholdHistory.class);
        verify(historyStore).update(captor.capture());
        GoldThresholdHistory updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getThreshold()).isEqualByComparingTo("4600");
        assertThat(updated.getSetAt()).isEqualTo(NOW);
        assertThat(updated.getStatus()).isEqualTo(GoldThresholdHistory.STATUS_PENDING);
        verify(ops).set("gold:alert:threshold", "4600");
    }

    @Test
    void setThresholdCreatesNewWhenLatestTriggered() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        GoldThresholdHistoryStore historyStore = mock(GoldThresholdHistoryStore.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        GoldThresholdHistory existing = new GoldThresholdHistory();
        existing.setId(1L);
        existing.setStatus(GoldThresholdHistory.STATUS_TRIGGERED);
        when(historyStore.findLatest()).thenReturn(Optional.of(existing));
        when(redisTemplate.opsForValue()).thenReturn(ops);

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate, historyStore, clock);

        store.setThreshold(new BigDecimal("4700"));

        ArgumentCaptor<GoldThresholdHistory> captor = ArgumentCaptor.forClass(GoldThresholdHistory.class);
        verify(historyStore).save(captor.capture());
        GoldThresholdHistory saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getThreshold()).isEqualByComparingTo("4700");
        assertThat(saved.getStatus()).isEqualTo(GoldThresholdHistory.STATUS_PENDING);
        verify(ops).set("gold:alert:threshold", "4700");
    }

    @Test
    void markTriggeredReturnsFalseWhenNoPending() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        GoldThresholdHistoryStore historyStore = mock(GoldThresholdHistoryStore.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(historyStore.findLatestPending()).thenReturn(Optional.empty());

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate, historyStore, clock);

        assertThat(store.markTriggered(NOW, new BigDecimal("4800"))).isFalse();
        verify(historyStore).findLatestPending();
        verifyNoMoreInteractions(historyStore);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void markTriggeredClearsCacheWhenUpdated() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        GoldThresholdHistoryStore historyStore = mock(GoldThresholdHistoryStore.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        GoldThresholdHistory active = new GoldThresholdHistory();
        active.setId(5L);
        active.setStatus(GoldThresholdHistory.STATUS_PENDING);
        when(historyStore.findLatestPending()).thenReturn(Optional.of(active));
        when(historyStore.markTriggered(5L, NOW, new BigDecimal("4800"))).thenReturn(true);

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate, historyStore, clock);

        assertThat(store.markTriggered(NOW, new BigDecimal("4800"))).isTrue();
        verify(redisTemplate).delete("gold:alert:threshold");
    }

    @Test
    void clearThresholdMarksCleared() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        GoldThresholdHistoryStore historyStore = mock(GoldThresholdHistoryStore.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        GoldThresholdHistory active = new GoldThresholdHistory();
        active.setId(7L);
        active.setStatus(GoldThresholdHistory.STATUS_PENDING);
        when(historyStore.findLatestPending()).thenReturn(Optional.of(active));

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate, historyStore, clock);

        store.clearThreshold();

        verify(historyStore).markCleared(7L);
        verify(redisTemplate).delete("gold:alert:threshold");
    }
}
