package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.service.GoldAlertLevelConfig;
import com.xbleey.goldpricealert.service.GoldAlertLevelConfigStore;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import com.xbleey.goldpricealert.service.GoldAlertEmailService;
import com.xbleey.goldpricealert.service.GoldAlertMessage;
import com.xbleey.goldpricealert.service.GoldMailRecipientService;
import com.xbleey.goldpricealert.service.GoldPriceHistory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/test")
public class TestController {

    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter ALERT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GoldAlertEmailService emailService;
    private final GoldMailRecipientService recipientService;
    private final GoldPriceHistory history;
    private final GoldAlertLevelConfigStore levelConfigStore;
    private final Clock clock;

    public TestController(
            GoldAlertEmailService emailService,
            GoldMailRecipientService recipientService,
            GoldPriceHistory history,
            GoldAlertLevelConfigStore levelConfigStore,
            Clock clock
    ) {
        this.emailService = emailService;
        this.recipientService = recipientService;
        this.history = history;
        this.levelConfigStore = levelConfigStore;
        this.clock = clock;
    }

    @PostMapping("/email")
    public String sendTestEmail() {
        if (recipientService.listEnabledEmails().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipients not configured");
        }
        List<GoldPriceSnapshot> snapshots = history.getRecent(60);
        if (snapshots.isEmpty()) {
            return "No snapshots found in database. Fetch prices first.";
        }
        GoldPriceSnapshot baseline = snapshots.getFirst();
        GoldPriceSnapshot latest = snapshots.getLast();
        BigDecimal baselinePrice = baseline.price();
        BigDecimal latestPrice = latest.price();
        BigDecimal changePercent = computeChangePercent(baselinePrice, latestPrice);
        GoldAlertLevelConfig level = resolveTestLevel();
        Duration window = resolveWindow(baseline.fetchedAt(), latest.fetchedAt());
        String alertTime = formatInstant(latest.fetchedAt());
        GoldAlertMessage message = new GoldAlertMessage(
                level.levelName(),
                level.levelRank(),
                "WARNING!!WARNING!!WARNING!! level: " + level.levelName()
                        + " | window=" + window
                        + " threshold=" + formatPercent(level.thresholdPercent()) + "%, change="
                        + formatPercent(changePercent) + "%, price "
                        + formatPrice(baselinePrice) + " -> " + formatPrice(latestPrice)
                        + " time=" + alertTime,
                latest.fetchedAt(),
                window,
                level.thresholdPercent(),
                changePercent,
                baselinePrice,
                latestPrice,
                snapshots
        );
        emailService.notifyAlert(message);
        return "Triggered test email send with " + snapshots.size() + " snapshots.";
    }

    private GoldAlertLevelConfig resolveTestLevel() {
        return levelConfigStore.findLevel("P4")
                .or(() -> levelConfigStore.listLevels().stream().findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "alert levels not configured"));
    }

    private BigDecimal computeChangePercent(BigDecimal baselinePrice, BigDecimal latestPrice) {
        if (baselinePrice == null || latestPrice == null) {
            return BigDecimal.ZERO;
        }
        if (baselinePrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return latestPrice
                .subtract(baselinePrice, MATH_CONTEXT)
                .divide(baselinePrice, MATH_CONTEXT)
                .multiply(ONE_HUNDRED, MATH_CONTEXT);
    }

    private Duration resolveWindow(Instant baselineTime, Instant latestTime) {
        if (baselineTime == null || latestTime == null) {
            return Duration.ZERO;
        }
        if (latestTime.isBefore(baselineTime)) {
            return Duration.between(latestTime, baselineTime);
        }
        return Duration.between(baselineTime, latestTime);
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return ALERT_TIME_FORMATTER.withZone(clock.getZone()).format(instant);
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "-" : price.toPlainString();
    }
}
