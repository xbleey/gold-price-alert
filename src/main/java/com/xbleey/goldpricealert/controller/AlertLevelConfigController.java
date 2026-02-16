package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.service.GoldAlertLevelConfig;
import com.xbleey.goldpricealert.service.GoldAlertLevelConfigStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/alert/levels")
public class AlertLevelConfigController {

    private final GoldAlertLevelConfigStore configStore;

    public AlertLevelConfigController(GoldAlertLevelConfigStore configStore) {
        this.configStore = configStore;
    }

    @GetMapping
    public Map<String, Object> listLevels() {
        List<GoldAlertLevelConfig> records = configStore.listLevels();
        return Map.of(
                "total", records.size(),
                "records", records
        );
    }

    @GetMapping("/{levelName}")
    public ResponseEntity<Map<String, Object>> getLevel(@PathVariable("levelName") String levelName) {
        try {
            GoldAlertLevelConfig record = configStore.getLevel(levelName);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "record", record
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (NoSuchElementException ex) {
            return notFound(ex.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createLevel(
            @RequestBody(required = false) AlertLevelUpsertRequest request
    ) {
        if (request == null) {
            return badRequest("request body must not be null");
        }
        try {
            GoldAlertLevelConfig created = configStore.createLevel(
                    request.levelName(),
                    request.thresholdPercent(),
                    request.window(),
                    request.cooldown()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "created",
                    "record", created
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @PutMapping("/{levelName}")
    public ResponseEntity<Map<String, Object>> updateLevel(
            @PathVariable("levelName") String levelName,
            @RequestBody(required = false) AlertLevelUpdateRequest request
    ) {
        if (request == null) {
            return badRequest("request body must not be null");
        }
        try {
            GoldAlertLevelConfig updated = configStore.updateLevel(
                    levelName,
                    request.thresholdPercent(),
                    request.window(),
                    request.cooldown()
            );
            return ResponseEntity.ok(Map.of(
                    "status", "updated",
                    "record", updated
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (NoSuchElementException ex) {
            return notFound(ex.getMessage());
        }
    }

    @DeleteMapping("/{levelName}")
    public ResponseEntity<Map<String, Object>> deleteLevel(@PathVariable("levelName") String levelName) {
        try {
            boolean deleted = configStore.deleteLevel(levelName);
            if (!deleted) {
                return notFound("level not found: " + levelName);
            }
            return ResponseEntity.ok(Map.of(
                    "status", "deleted",
                    "levelName", levelName.toUpperCase()
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return response(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    private ResponseEntity<Map<String, Object>> notFound(String message) {
        return response(HttpStatus.NOT_FOUND, "not_found", message);
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    public record AlertLevelUpsertRequest(
            String levelName,
            BigDecimal thresholdPercent,
            Integer window,
            Integer cooldown
    ) {
    }

    public record AlertLevelUpdateRequest(
            BigDecimal thresholdPercent,
            Integer window,
            Integer cooldown
    ) {
    }
}
