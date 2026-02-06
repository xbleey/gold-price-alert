package com.xbleey.goldpricealert.controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-02-06T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void livenessShouldReturnUp() {
        HealthController controller = new HealthController(FIXED_CLOCK, null, null);

        Map<String, Object> response = controller.liveness();

        assertThat(response).containsEntry("status", "UP");
        assertThat(response).containsEntry("timestamp", "2026-02-06T12:00:00Z");
    }

    @Test
    void readinessShouldReturnUpWhenChecksAreSkipped() {
        HealthController controller = new HealthController(FIXED_CLOCK, null, null);

        ResponseEntity<Map<String, Object>> response = controller.readiness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
        Map<String, Object> checks = asMap(response.getBody().get("checks"));
        assertThat(asMap(checks.get("database"))).containsEntry("status", "SKIPPED");
        assertThat(asMap(checks.get("redis"))).containsEntry("status", "SKIPPED");
    }

    @Test
    void readinessShouldReturn503WhenDatabaseValidationFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);

        HealthController controller = new HealthController(FIXED_CLOCK, dataSource, null);

        ResponseEntity<Map<String, Object>> response = controller.readiness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
        Map<String, Object> checks = asMap(response.getBody().get("checks"));
        assertThat(asMap(checks.get("database"))).containsEntry("status", "DOWN");
    }

    @Test
    void readinessShouldReturn503WhenRedisPingIsUnexpected() {
        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("ERR");

        HealthController controller = new HealthController(FIXED_CLOCK, null, redisFactory);

        ResponseEntity<Map<String, Object>> response = controller.readiness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
        Map<String, Object> checks = asMap(response.getBody().get("checks"));
        assertThat(asMap(checks.get("redis"))).containsEntry("status", "DOWN");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
