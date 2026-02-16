package com.xbleey.goldpricealert.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
public class GoldAlertLevelConfigStore {

    private static final Logger log = LoggerFactory.getLogger(GoldAlertLevelConfigStore.class);
    private static final String ALERT_LEVEL_CONFIG_KEY = "gold:alert:levels:config";
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final TypeReference<List<GoldAlertLevelConfig>> LEVEL_LIST_TYPE =
            new TypeReference<>() {
            };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Object lock = new Object();

    public GoldAlertLevelConfigStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<GoldAlertLevelConfig> listLevels() {
        synchronized (lock) {
            return loadOrBootstrap();
        }
    }

    public Optional<GoldAlertLevelConfig> findLevel(String levelName) {
        String normalized = normalizeLevelName(levelName);
        return listLevels().stream()
                .filter(config -> config.levelName().equals(normalized))
                .findFirst();
    }

    public GoldAlertLevelConfig getLevel(String levelName) {
        String normalized = normalizeLevelName(levelName);
        return findLevel(normalized)
                .orElseThrow(() -> new NoSuchElementException("level not found: " + normalized));
    }

    public GoldAlertLevelConfig createLevel(
            String levelName,
            BigDecimal thresholdPercent,
            Integer windowMinutes,
            Integer cooldownMinutes
    ) {
        synchronized (lock) {
            String normalized = normalizeLevelName(levelName);
            int rank = GoldAlertLevelName.rankOf(normalized);
            if (rank <= 5) {
                throw new IllegalArgumentException("P1~P5 are reserved and cannot be created");
            }
            List<GoldAlertLevelConfig> current = loadOrBootstrap();
            if (containsLevel(current, normalized)) {
                throw new IllegalArgumentException("level already exists: " + normalized);
            }
            GoldAlertLevelConfig created = buildEditableConfig(normalized, thresholdPercent, windowMinutes, cooldownMinutes);
            List<GoldAlertLevelConfig> updated = new ArrayList<>(current);
            updated.add(created);
            updated.sort(Comparator.comparingInt(GoldAlertLevelConfig::levelRank));
            List<GoldAlertLevelConfig> snapshot = List.copyOf(updated);
            persist(snapshot);
            return created;
        }
    }

    public GoldAlertLevelConfig updateLevel(
            String levelName,
            BigDecimal thresholdPercent,
            Integer windowMinutes,
            Integer cooldownMinutes
    ) {
        synchronized (lock) {
            String normalized = normalizeLevelName(levelName);
            List<GoldAlertLevelConfig> current = loadOrBootstrap();
            if (!containsLevel(current, normalized)) {
                throw new NoSuchElementException("level not found: " + normalized);
            }
            GoldAlertLevelConfig replacement = buildUpdatedConfig(
                    normalized,
                    thresholdPercent,
                    windowMinutes,
                    cooldownMinutes
            );
            List<GoldAlertLevelConfig> updated = new ArrayList<>(current.size());
            for (GoldAlertLevelConfig config : current) {
                if (config.levelName().equals(normalized)) {
                    updated.add(replacement);
                } else {
                    updated.add(config);
                }
            }
            updated.sort(Comparator.comparingInt(GoldAlertLevelConfig::levelRank));
            List<GoldAlertLevelConfig> snapshot = List.copyOf(updated);
            persist(snapshot);
            return replacement;
        }
    }

    public boolean deleteLevel(String levelName) {
        synchronized (lock) {
            String normalized = normalizeLevelName(levelName);
            int rank = GoldAlertLevelName.rankOf(normalized);
            if (rank <= 5) {
                throw new IllegalArgumentException("P1~P5 are fixed and cannot be deleted");
            }
            List<GoldAlertLevelConfig> current = loadOrBootstrap();
            List<GoldAlertLevelConfig> updated = current.stream()
                    .filter(config -> !config.levelName().equals(normalized))
                    .toList();
            if (updated.size() == current.size()) {
                return false;
            }
            List<GoldAlertLevelConfig> snapshot = List.copyOf(updated);
            persist(snapshot);
            return true;
        }
    }

    private boolean containsLevel(List<GoldAlertLevelConfig> configs, String levelName) {
        return configs.stream().anyMatch(config -> config.levelName().equals(levelName));
    }

    private GoldAlertLevelConfig buildEditableConfig(
            String levelName,
            BigDecimal thresholdPercent,
            Integer windowMinutes,
            Integer cooldownMinutes
    ) {
        validateThresholdForEditable(thresholdPercent);
        int window = validateMinutes(windowMinutes, "window");
        int cooldown = validateMinutes(cooldownMinutes, "cooldown");
        return new GoldAlertLevelConfig(
                levelName,
                GoldAlertLevelName.rankOf(levelName),
                thresholdPercent.stripTrailingZeros(),
                window,
                cooldown,
                false
        );
    }

    private GoldAlertLevelConfig buildUpdatedConfig(
            String levelName,
            BigDecimal thresholdPercent,
            Integer windowMinutes,
            Integer cooldownMinutes
    ) {
        validateThresholdForEditable(thresholdPercent);
        int window = validateMinutes(windowMinutes, "window");
        int cooldown = validateMinutes(cooldownMinutes, "cooldown");
        int rank = GoldAlertLevelName.rankOf(levelName);
        return new GoldAlertLevelConfig(
                levelName,
                rank,
                thresholdPercent.stripTrailingZeros(),
                window,
                cooldown,
                rank <= 5
        );
    }

    private int validateMinutes(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
        return value;
    }

    private void validateThresholdForEditable(BigDecimal thresholdPercent) {
        if (thresholdPercent == null) {
            throw new IllegalArgumentException("thresholdPercent must not be null");
        }
        if (thresholdPercent.scale() > 2) {
            throw new IllegalArgumentException("thresholdPercent scale must be <= 2");
        }
        if (thresholdPercent.compareTo(ZERO) < 0 || thresholdPercent.compareTo(TEN) > 0) {
            throw new IllegalArgumentException("thresholdPercent must be between 0 and 10");
        }
    }

    private String normalizeLevelName(String levelName) {
        String normalized = GoldAlertLevelName.normalize(levelName);
        if (!GoldAlertLevelName.isValid(normalized)) {
            throw new IllegalArgumentException("invalid levelName, expected P<number>: " + levelName);
        }
        return normalized;
    }

    private List<GoldAlertLevelConfig> loadOrBootstrap() {
        String cached = null;
        try {
            cached = redisTemplate.opsForValue().get(ALERT_LEVEL_CONFIG_KEY);
        } catch (Exception ex) {
            log.warn("Failed to read alert level config from redis", ex);
        }
        if (cached == null || cached.isBlank()) {
            List<GoldAlertLevelConfig> defaults = defaultConfigs();
            persist(defaults);
            return defaults;
        }
        try {
            List<GoldAlertLevelConfig> parsed = objectMapper.readValue(cached, LEVEL_LIST_TYPE);
            List<GoldAlertLevelConfig> canonical = canonicalize(parsed);
            if (!Objects.equals(canonical, parsed)) {
                persist(canonical);
            }
            return canonical;
        } catch (Exception ex) {
            log.warn("Failed to parse alert level config from redis, reinitializing defaults", ex);
            List<GoldAlertLevelConfig> defaults = defaultConfigs();
            persist(defaults);
            return defaults;
        }
    }

    private List<GoldAlertLevelConfig> canonicalize(List<GoldAlertLevelConfig> raw) {
        Map<String, GoldAlertLevelConfig> merged = new LinkedHashMap<>();
        if (raw != null) {
            for (GoldAlertLevelConfig config : raw) {
                if (config == null || config.levelName() == null) {
                    continue;
                }
                String normalized = GoldAlertLevelName.normalize(config.levelName());
                if (!GoldAlertLevelName.isValid(normalized)) {
                    continue;
                }
                int rank = GoldAlertLevelName.rankOf(normalized);
                if (rank <= 5) {
                    if (!isProtectedConfigValid(config)) {
                        continue;
                    }
                    merged.put(normalized, new GoldAlertLevelConfig(
                            normalized,
                            rank,
                            config.thresholdPercent().stripTrailingZeros(),
                            config.windowMinutes(),
                            config.cooldownMinutes(),
                            true
                    ));
                    continue;
                }
                if (!isEditableConfigValid(config)) {
                    continue;
                }
                merged.put(normalized, new GoldAlertLevelConfig(
                        normalized,
                        rank,
                        config.thresholdPercent().stripTrailingZeros(),
                        config.windowMinutes(),
                        config.cooldownMinutes(),
                        false
                ));
            }
        }
        for (GoldAlertLevelConfig defaults : defaultConfigs()) {
            merged.putIfAbsent(defaults.levelName(), defaults);
        }
        return merged.values().stream()
                .sorted(Comparator.comparingInt(GoldAlertLevelConfig::levelRank))
                .toList();
    }

    private boolean isProtectedConfigValid(GoldAlertLevelConfig config) {
        try {
            validateThresholdForEditable(config.thresholdPercent());
            validateMinutes(config.windowMinutes(), "window");
            validateMinutes(config.cooldownMinutes(), "cooldown");
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isEditableConfigValid(GoldAlertLevelConfig config) {
        try {
            validateThresholdForEditable(config.thresholdPercent());
            validateMinutes(config.windowMinutes(), "window");
            validateMinutes(config.cooldownMinutes(), "cooldown");
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private List<GoldAlertLevelConfig> defaultConfigs() {
        List<GoldAlertLevelConfig> defaults = new ArrayList<>();
        for (GoldAlertLevel level : GoldAlertLevel.values()) {
            int rank = GoldAlertLevelName.rankOf(level.getLevelName());
            defaults.add(new GoldAlertLevelConfig(
                    level.getLevelName(),
                    rank,
                    level.getThresholdPercent(),
                    (int) level.getWindow().toMinutes(),
                    level.getDefaultCooldownMinutes(),
                    true
            ));
        }
        defaults.sort(Comparator.comparingInt(GoldAlertLevelConfig::levelRank));
        return List.copyOf(defaults);
    }

    private void persist(List<GoldAlertLevelConfig> configs) {
        try {
            redisTemplate.opsForValue().set(ALERT_LEVEL_CONFIG_KEY, objectMapper.writeValueAsString(configs));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to persist alert level config to redis", ex);
        }
    }
}
