package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.service.GoldThresholdStore;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/threshold")
public class GoldThresholdController {

    private final GoldThresholdStore thresholdStore;

    public GoldThresholdController(GoldThresholdStore thresholdStore) {
        this.thresholdStore = thresholdStore;
    }

    @GetMapping
    public Map<String, Object> getThreshold() {
        Optional<BigDecimal> threshold = thresholdStore.getThreshold();
        Map<String, Object> response = new HashMap<>();
        response.put("threshold", threshold.map(BigDecimal::toPlainString).orElse(null));
        response.put("status", threshold.isPresent() ? "ok" : "not_set");
        return response;
    }

    @PostMapping
    public Map<String, Object> setThreshold(@RequestParam("value") BigDecimal value) {
        BigDecimal saved = thresholdStore.setThreshold(value);
        return Map.of(
                "threshold", saved.toPlainString(),
                "status", "ok"
        );
    }

    @DeleteMapping
    public Map<String, Object> clearThreshold() {
        thresholdStore.clearThreshold();
        return Map.of("status", "cleared");
    }
}
