package com.xbleey.goldpricealert.controller;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private static final int CONNECTION_TIMEOUT_SECONDS = 2;

    private final Clock clock;
    @Nullable
    private final DataSource dataSource;
    @Nullable
    private final RedisConnectionFactory redisConnectionFactory;

    public HealthController(
            Clock clock,
            @Nullable DataSource dataSource,
            @Nullable RedisConnectionFactory redisConnectionFactory
    ) {
        this.clock = clock;
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping("/live")
    public Map<String, Object> liveness() {
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now(clock).toString()
        );
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return readiness();
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Map<String, Object>> checks = new LinkedHashMap<>();
        boolean ready = true;

        ready &= checkDatabase(checks);
        ready &= checkRedis(checks);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", ready ? "UP" : "DOWN");
        body.put("timestamp", Instant.now(clock).toString());
        body.put("checks", checks);

        if (ready) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean checkDatabase(Map<String, Map<String, Object>> checks) {
        if (dataSource == null) {
            checks.put("database", Map.of("status", "SKIPPED"));
            return true;
        }
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(CONNECTION_TIMEOUT_SECONDS)) {
                checks.put("database", Map.of("status", "UP"));
                return true;
            }
            checks.put("database", Map.of(
                    "status", "DOWN",
                    "message", "Connection validation returned false"
            ));
            return false;
        } catch (Exception ex) {
            checks.put("database", Map.of(
                    "status", "DOWN",
                    "message", ex.getClass().getSimpleName() + ": " + messageOrDefault(ex.getMessage())
            ));
            return false;
        }
    }

    private boolean checkRedis(Map<String, Map<String, Object>> checks) {
        if (redisConnectionFactory == null) {
            checks.put("redis", Map.of("status", "SKIPPED"));
            return true;
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String ping = connection.ping();
            if ("PONG".equalsIgnoreCase(ping)) {
                checks.put("redis", Map.of("status", "UP"));
                return true;
            }
            checks.put("redis", Map.of(
                    "status", "DOWN",
                    "message", "Unexpected ping response: " + messageOrDefault(ping)
            ));
            return false;
        } catch (Exception ex) {
            checks.put("redis", Map.of(
                    "status", "DOWN",
                    "message", ex.getClass().getSimpleName() + ": " + messageOrDefault(ex.getMessage())
            ));
            return false;
        }
    }

    private String messageOrDefault(String message) {
        if (message == null || message.isBlank()) {
            return "-";
        }
        return message;
    }
}
