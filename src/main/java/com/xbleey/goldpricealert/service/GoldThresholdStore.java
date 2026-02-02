package com.xbleey.goldpricealert.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class GoldThresholdStore {

    private static final Logger log = LoggerFactory.getLogger(GoldThresholdStore.class);
    private static final String THRESHOLD_KEY = "gold:alert:threshold";

    private final StringRedisTemplate redisTemplate;

    public GoldThresholdStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<BigDecimal> getThreshold() {
        String value;
        try {
            value = redisTemplate.opsForValue().get(THRESHOLD_KEY);
        } catch (Exception ex) {
            log.warn("Failed to read threshold from redis", ex);
            return Optional.empty();
        }
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(value.trim()));
        } catch (NumberFormatException ex) {
            log.warn("Invalid threshold value stored in redis: {}", value);
            return Optional.empty();
        }
    }

    public BigDecimal setThreshold(BigDecimal threshold) {
        if (threshold == null) {
            throw new IllegalArgumentException("threshold must not be null");
        }
        redisTemplate.opsForValue().set(THRESHOLD_KEY, threshold.toPlainString());
        return threshold;
    }

    public void clearThreshold() {
        try {
            redisTemplate.delete(THRESHOLD_KEY);
        } catch (Exception ex) {
            log.warn("Failed to clear threshold from redis", ex);
        }
    }
}
