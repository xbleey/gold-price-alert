package com.xbleey.goldpricealert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoldAlertLevelConfigStoreTest {

    private static final String LEVEL_KEY = "gold:alert:levels:config";

    @Test
    void listLevelsBootstrapsDefaultsWhenRedisEmpty() {
        Fixture fixture = newFixture();
        GoldAlertLevelConfigStore store = fixture.store();

        List<GoldAlertLevelConfig> levels = store.listLevels();

        assertThat(levels).hasSize(5);
        assertThat(levels.getFirst().levelName()).isEqualTo("P1");
        assertThat(levels.getLast().levelName()).isEqualTo("P5");
        assertThat(fixture.cache().get()).isNotBlank();
    }

    @Test
    void protectedLevelCannotBeDeleted() {
        Fixture fixture = newFixture();
        GoldAlertLevelConfigStore store = fixture.store();
        store.listLevels();

        assertThatThrownBy(() -> store.deleteLevel("P1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be deleted");
    }

    @Test
    void createUpdateDeleteWorksForP6() {
        Fixture fixture = newFixture();
        GoldAlertLevelConfigStore store = fixture.store();

        GoldAlertLevelConfig created = store.createLevel("P6", new BigDecimal("0.99"), 10, 2);
        assertThat(created.levelName()).isEqualTo("P6");
        assertThat(created.thresholdPercent()).isEqualByComparingTo("0.99");
        assertThat(store.listLevels()).extracting(GoldAlertLevelConfig::levelName).contains("P6");

        GoldAlertLevelConfig updated = store.updateLevel("P6", new BigDecimal("0.20"), 8, 1);
        assertThat(updated.thresholdPercent()).isEqualByComparingTo("0.20");
        assertThat(updated.windowMinutes()).isEqualTo(8);
        assertThat(updated.cooldownMinutes()).isEqualTo(1);

        assertThat(store.deleteLevel("P6")).isTrue();
        assertThat(store.listLevels()).extracting(GoldAlertLevelConfig::levelName).doesNotContain("P6");
    }

    @Test
    void updateAllowsProtectedPLevels() {
        Fixture fixture = newFixture();
        GoldAlertLevelConfigStore store = fixture.store();
        store.listLevels();

        GoldAlertLevelConfig updated = store.updateLevel("P1", new BigDecimal("0.20"), 2, 1);

        assertThat(updated.levelName()).isEqualTo("P1");
        assertThat(updated.levelRank()).isEqualTo(1);
        assertThat(updated.thresholdPercent()).isEqualByComparingTo("0.20");
        assertThat(updated.windowMinutes()).isEqualTo(2);
        assertThat(updated.cooldownMinutes()).isEqualTo(1);
        assertThat(updated.protectedLevel()).isTrue();
    }

    @Test
    void createRejectsInvalidThresholdAndWindow() {
        Fixture fixture = newFixture();
        GoldAlertLevelConfigStore store = fixture.store();
        store.listLevels();

        assertThatThrownBy(() -> store.createLevel("P6", new BigDecimal("1.01"), 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 1");

        assertThatThrownBy(() -> store.createLevel("P6", new BigDecimal("0.123"), 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale must be <= 2");

        assertThatThrownBy(() -> store.createLevel("P6", new BigDecimal("0.12"), -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window must be >= 0");
    }

    private Fixture newFixture() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        AtomicReference<String> cache = new AtomicReference<>();
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(invocation -> cache.get());
        doAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            String value = invocation.getArgument(1, String.class);
            if (LEVEL_KEY.equals(key)) {
                cache.set(value);
            }
            return null;
        }).when(ops).set(anyString(), anyString());
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        GoldAlertLevelConfigStore store = new GoldAlertLevelConfigStore(redisTemplate, objectMapper);
        return new Fixture(store, cache);
    }

    private record Fixture(GoldAlertLevelConfigStore store, AtomicReference<String> cache) {
    }
}
