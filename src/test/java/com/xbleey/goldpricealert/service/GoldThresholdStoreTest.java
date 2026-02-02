package com.xbleey.goldpricealert.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoldThresholdStoreTest {

    private static final String THRESHOLD_KEY = "gold:alert:threshold";

    @Test
    void getThresholdReturnsEmptyWhenMissing() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(THRESHOLD_KEY)).thenReturn(null);

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate);

        assertThat(store.getThreshold()).isEmpty();
    }

    @Test
    void getThresholdReturnsEmptyWhenBlank() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(THRESHOLD_KEY)).thenReturn("   ");

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate);

        assertThat(store.getThreshold()).isEmpty();
    }

    @Test
    void getThresholdReturnsValueWhenParsable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(THRESHOLD_KEY)).thenReturn("4500");

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate);

        Optional<BigDecimal> threshold = store.getThreshold();
        assertThat(threshold).contains(new BigDecimal("4500"));
    }

    @Test
    void getThresholdReturnsEmptyWhenInvalidNumber() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(THRESHOLD_KEY)).thenReturn("invalid");

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate);

        assertThat(store.getThreshold()).isEmpty();
    }

    @Test
    void getThresholdReturnsEmptyWhenRedisThrows() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(THRESHOLD_KEY)).thenThrow(new RuntimeException("boom"));

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate);

        assertThat(store.getThreshold()).isEmpty();
    }

    @Test
    void setThresholdWritesToRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate);

        BigDecimal saved = store.setThreshold(new BigDecimal("4500"));

        assertThat(saved).isEqualByComparingTo("4500");
        verify(ops).set(THRESHOLD_KEY, "4500");
    }

    @Test
    void clearThresholdSwallowsRedisExceptions() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("boom")).when(redisTemplate).delete(THRESHOLD_KEY);

        GoldThresholdStore store = new GoldThresholdStore(redisTemplate);

        store.clearThreshold();
    }
}
