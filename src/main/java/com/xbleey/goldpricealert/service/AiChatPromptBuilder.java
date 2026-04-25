package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class AiChatPromptBuilder {

    public String buildSystemPrompt(List<GoldPriceSnapshot> recentSnapshots) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                你是一个黄金市场中文对话助手，主要围绕 XAU/USD 黄金价格、波动、告警、风险和宏观影响因素回答问题。
                回答时优先使用系统提供的最近行情快照，并明确引用数据时间。
                如果系统没有提供实时行情，必须说明当前系统暂无最新行情，不要编造实时价格。
                不承诺收益，不给保证性的买入或卖出结论；可以提供分析框架、风险提示和可验证的观察指标。
                """);
        prompt.append("\n系统提供的最近 XAU/USD 快照：\n");
        if (recentSnapshots == null || recentSnapshots.isEmpty()) {
            prompt.append("- 暂无最近行情快照。\n");
            return prompt.toString();
        }
        for (GoldPriceSnapshot snapshot : recentSnapshots) {
            prompt.append("- fetchedAt=")
                    .append(formatInstant(snapshot))
                    .append(", price=")
                    .append(formatPrice(snapshot.getPrice()))
                    .append(", symbol=")
                    .append(blankToUnknown(snapshot.getSymbol()))
                    .append(", sourceUpdatedAt=")
                    .append(snapshot.getUpdatedAt() == null ? "unknown" : DateTimeFormatter.ISO_INSTANT.format(snapshot.getUpdatedAt()))
                    .append("\n");
        }
        return prompt.toString();
    }

    private String formatInstant(GoldPriceSnapshot snapshot) {
        if (snapshot == null || snapshot.getFetchedAt() == null) {
            return "unknown";
        }
        return DateTimeFormatter.ISO_INSTANT.format(snapshot.getFetchedAt());
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "unknown" : price.toPlainString();
    }

    private String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
