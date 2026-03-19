package com.xbleey.goldpricealert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import com.xbleey.goldpricealert.enums.GoldThresholdDirection;
import com.xbleey.goldpricealert.mapper.GoldAlertHistoryMapper;
import com.xbleey.goldpricealert.model.GoldAlertHistory;
import com.xbleey.goldpricealert.service.GoldAlertLevelName;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/alert")
public class AlertController {

    private final GoldAlertHistoryMapper alertHistoryMapper;

    public AlertController(GoldAlertHistoryMapper alertHistoryMapper) {
        this.alertHistoryMapper = alertHistoryMapper;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(name = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(name = "pageSize", defaultValue = "20") long pageSize,
            @RequestParam(name = "alertLevel", required = false) List<String> alertLevels,
            @RequestParam(name = "direction", required = false) List<String> directions
    ) {
        long safePageNum = Math.max(1L, pageNum);
        long safePageSize = Math.clamp(pageSize, 1L, 200L);
        Page<GoldAlertHistory> page = Page.of(safePageNum, safePageSize);

        LambdaQueryWrapper<GoldAlertHistory> wrapper = new LambdaQueryWrapper<>();
        List<String> normalizedAlertLevels = normalizeAlertLevels(alertLevels);
        if (!normalizedAlertLevels.isEmpty()) {
            wrapper.in(GoldAlertHistory::getAlertLevel, normalizedAlertLevels);
        }
        List<GoldThresholdDirection> normalizedDirections = normalizeDirections(directions);
        if (normalizedDirections.size() == 1) {
            if (normalizedDirections.getFirst() == GoldThresholdDirection.UP) {
                wrapper.gt(GoldAlertHistory::getChangePercent, BigDecimal.ZERO);
            } else {
                wrapper.lt(GoldAlertHistory::getChangePercent, BigDecimal.ZERO);
            }
        }
        wrapper.orderByDesc(GoldAlertHistory::getAlertTimeUtc)
                .orderByDesc(GoldAlertHistory::getId);

        Page<GoldAlertHistory> result = alertHistoryMapper.selectPage(page, wrapper);
        return Map.of(
                "current", result.getCurrent(),
                "pageSize", result.getSize(),
                "total", result.getTotal(),
                "pages", result.getPages(),
                "records", result.getRecords()
        );
    }

    private List<String> normalizeAlertLevels(List<String> alertLevels) {
        if (alertLevels == null || alertLevels.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String level : alertLevels) {
            if (level == null || level.isBlank()) {
                continue;
            }
            for (String part : level.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(resolveAlertLevel(trimmed));
                }
            }
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.copyOf(normalized);
    }

    private List<GoldThresholdDirection> normalizeDirections(List<String> directions) {
        if (directions == null || directions.isEmpty()) {
            return List.of();
        }
        Set<GoldThresholdDirection> normalized = new LinkedHashSet<>();
        for (String direction : directions) {
            if (direction == null || direction.isBlank()) {
                continue;
            }
            for (String part : direction.split(",")) {
                GoldThresholdDirection resolved = resolveDirection(part);
                if (resolved != null) {
                    normalized.add(resolved);
                }
            }
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.copyOf(normalized);
    }

    private String resolveAlertLevel(String rawLevel) {
        String normalizedInput = rawLevel.trim();
        if (normalizedInput.isEmpty()) {
            return normalizedInput;
        }
        if (GoldAlertLevelName.isValid(normalizedInput)) {
            return GoldAlertLevelName.normalize(normalizedInput);
        }

        String upperInput = normalizedInput.toUpperCase(Locale.ROOT);
        for (GoldAlertLevel level : GoldAlertLevel.values()) {
            if (level.name().equals(upperInput)
                    || level.getLevelName().equalsIgnoreCase(normalizedInput)) {
                return level.getLevelName();
            }
        }

        return normalizedInput;
    }

    private GoldThresholdDirection resolveDirection(String rawDirection) {
        String normalizedInput = rawDirection == null ? "" : rawDirection.trim();
        if (normalizedInput.isEmpty()) {
            return null;
        }

        String upperInput = normalizedInput.toUpperCase(Locale.ROOT);
        if ("UP".equals(upperInput)
                || "RISE".equals(upperInput)
                || "涨".equals(normalizedInput)
                || "上涨".equals(normalizedInput)
                || "上升".equals(normalizedInput)) {
            return GoldThresholdDirection.UP;
        }
        if ("DOWN".equals(upperInput)
                || "FALL".equals(upperInput)
                || "跌".equals(normalizedInput)
                || "下跌".equals(normalizedInput)
                || "下降".equals(normalizedInput)) {
            return GoldThresholdDirection.DOWN;
        }
        return null;
    }
}
