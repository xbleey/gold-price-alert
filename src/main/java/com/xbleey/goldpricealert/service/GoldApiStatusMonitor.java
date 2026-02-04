package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.config.GoldProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class GoldApiStatusMonitor {

    private static final Duration ERROR_NOTIFY_INTERVAL = Duration.ofMinutes(10);

    private final GoldAlertEmailService emailService;
    private final GoldProperties properties;
    private final Clock clock;
    private final Object lock = new Object();
    private Instant firstFailureAt;
    private Instant lastNotificationAt;

    public GoldApiStatusMonitor(GoldAlertEmailService emailService, GoldProperties properties, Clock clock) {
        this.emailService = emailService;
        this.properties = properties;
        this.clock = clock;
    }

    public void recordFailure(String errorDetail) {
        Instant now = Instant.now(clock);
        GoldApiErrorMessage message = null;
        synchronized (lock) {
            if (firstFailureAt == null) {
                firstFailureAt = now;
            }
            if (shouldNotify(now)) {
                Duration downtime = Duration.between(firstFailureAt, now);
                message = new GoldApiErrorMessage(
                        now,
                        properties.getApiUrl() == null ? null : properties.getApiUrl().toString(),
                        errorDetail,
                        downtime
                );
                lastNotificationAt = now;
            }
        }
        if (message != null) {
            emailService.notifyApiError(message);
        }
    }

    public void recordSuccess() {
        GoldApiResumeMessage message = null;
        Instant now = Instant.now(clock);
        synchronized (lock) {
            if (firstFailureAt == null) {
                return;
            }
            Duration downtime = Duration.between(firstFailureAt, now);
            message = new GoldApiResumeMessage(
                    now,
                    firstFailureAt,
                    downtime,
                    properties.getApiUrl() == null ? null : properties.getApiUrl().toString()
            );
            firstFailureAt = null;
            lastNotificationAt = null;
        }
        if (message != null) {
            emailService.notifyApiResume(message);
        }
    }

    private boolean shouldNotify(Instant now) {
        if (lastNotificationAt == null) {
            return true;
        }
        Duration elapsed = Duration.between(lastNotificationAt, now);
        return elapsed.compareTo(ERROR_NOTIFY_INTERVAL) >= 0;
    }
}
