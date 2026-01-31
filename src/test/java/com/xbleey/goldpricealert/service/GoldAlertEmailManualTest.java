package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import com.xbleey.goldpricealert.model.GoldApiResponse;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@Tag("manual")
class GoldAlertEmailManualTest {

    @Autowired
    private GoldAlertEmailService emailService;

    @Test
    void sendOnce() {
        Instant now = Instant.now();
        GoldAlertMessage message = new GoldAlertMessage(
                GoldAlertLevel.MAJOR_LEVEL,
                "ALERT!! level: P4 | window=PT1H threshold=0.9000%, change=1.0500%, price 1900.00 -> 1920.00 time="
                        + now,
                now,
                java.time.Duration.ofHours(1),
                new BigDecimal("0.9000"),
                new BigDecimal("1.0500"),
                new BigDecimal("1900.00"),
                new BigDecimal("1920.00"),
                buildSnapshots(now)
        );
        String html = emailService.previewHtml(message);
        System.out.println(html);
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
