package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import com.xbleey.goldpricealert.model.GoldApiResponse;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import com.xbleey.goldpricealert.service.GoldAlertEmailService;
import com.xbleey.goldpricealert.service.GoldAlertMessage;
import com.xbleey.goldpricealert.service.GoldPriceHistory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/test")
public class TestController {

    private final GoldAlertEmailService emailService;
    private final GoldPriceHistory history;
    private final Clock clock;

    public TestController(GoldAlertEmailService emailService, GoldPriceHistory history, Clock clock) {
        this.emailService = emailService;
        this.history = history;
        this.clock = clock;
    }

    @PostMapping("/email")
    public String sendTestEmail() {
        Instant now = Instant.now(clock);
        List<GoldPriceSnapshot> snapshots = history.getRecent(10);
        if (snapshots.isEmpty()) {
            snapshots = buildSampleSnapshots(now);
        }
        GoldAlertMessage message = new GoldAlertMessage(
                GoldAlertLevel.MAJOR_LEVEL,
                "WARNING!!WARNING!!WARNING!! level: P4 | window=PT1H threshold=1.0000%, change=1.0500%, price 1900.00 -> 1920.00 time="
                        + now,
                now,
                Duration.ofHours(1),
                new BigDecimal("1.0000"),
                new BigDecimal("1.0500"),
                new BigDecimal("1900.00"),
                new BigDecimal("1920.00"),
                snapshots
        );
        emailService.notifyAlert(message);
        return "Triggered test email send.";
    }

    private List<GoldPriceSnapshot> buildSampleSnapshots(Instant now) {
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
