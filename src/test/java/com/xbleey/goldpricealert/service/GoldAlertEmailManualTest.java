package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.config.GoldAlertMailProperties;
import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import com.xbleey.goldpricealert.model.GoldApiResponse;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Tag("manual")
class GoldAlertEmailManualTest {

    @Test
    void sendOnce() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-05T12:00:00Z"), ZoneOffset.UTC);
        GoldAlertMailProperties properties = mockProperties();
        JavaMailSender mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        GoldAlertEmailService emailService = new GoldAlertEmailService(mailSender, properties, clock);
        Instant now = Instant.now(clock);
        GoldAlertMessage message = new GoldAlertMessage(
                GoldAlertLevel.MAJOR_LEVEL,
                "ALERT!! level: P4 | window=PT1H threshold=1.0000%, change=1.0500%, price 1900.00 -> 1920.00 time="
                        + now,
                now,
                java.time.Duration.ofHours(1),
                new BigDecimal("1.0000"),
                new BigDecimal("1.0500"),
                new BigDecimal("1900.00"),
                new BigDecimal("1920.00"),
                buildSnapshots(now)
        );
        String html = emailService.previewHtml(message);
        System.out.println(html);
    }

    private static GoldAlertMailProperties mockProperties() {
        GoldAlertMailProperties properties = new GoldAlertMailProperties();
        properties.setSender("noreply@example.com");
        properties.setRecipients(List.of("receiver@example.com"));
        Map<GoldAlertLevel, Duration> cooldowns = new EnumMap<>(GoldAlertLevel.class);
        for (GoldAlertLevel level : GoldAlertLevel.values()) {
            cooldowns.put(level, Duration.ZERO);
        }
        properties.setCooldowns(cooldowns);
        properties.setMinLevel(GoldAlertLevel.INFO_LEVEL);
        return properties;
    }

    private List<GoldPriceSnapshot> buildSnapshots(Instant now) {
        List<GoldPriceSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Instant time = now.minusSeconds((9L - i) * 60);
            BigDecimal price = new BigDecimal("1900.00").add(new BigDecimal(i));
            GoldApiResponse response = new GoldApiResponse(
                    "gold",
                    price,
                    "XAU",
                    time,
                    time.toString()
            );
            snapshots.add(new GoldPriceSnapshot(time, response));
        }
        return List.copyOf(snapshots);
    }
}
