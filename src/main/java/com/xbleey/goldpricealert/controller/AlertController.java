package com.xbleey.goldpricealert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xbleey.goldpricealert.mapper.GoldAlertHistoryMapper;
import com.xbleey.goldpricealert.model.GoldAlertHistory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
            @RequestParam(name = "alertLevel", required = false) String alertLevel
    ) {
        long safePageNum = Math.max(1L, pageNum);
        long safePageSize = Math.min(Math.max(1L, pageSize), 200L);
        Page<GoldAlertHistory> page = Page.of(safePageNum, safePageSize);

        LambdaQueryWrapper<GoldAlertHistory> wrapper = new LambdaQueryWrapper<>();
        if (alertLevel != null && !alertLevel.isBlank()) {
            wrapper.eq(GoldAlertHistory::getAlertLevel, alertLevel.trim());
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
}
