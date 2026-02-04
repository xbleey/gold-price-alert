package com.xbleey.goldpricealert.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class GoldThresholdAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(GoldThresholdAlertScheduler.class);
    private static final List<Duration> SEND_DELAYS = List.of(
            Duration.ZERO,
            Duration.ofMinutes(1),
            Duration.ofMinutes(3),
            Duration.ofMinutes(6),
            Duration.ofMinutes(10)
    );

    private final TaskScheduler taskScheduler;
    private final GoldAlertEmailService emailService;
    private final Clock clock;

    public GoldThresholdAlertScheduler(TaskScheduler taskScheduler, GoldAlertEmailService emailService, Clock clock) {
        this.taskScheduler = taskScheduler;
        this.emailService = emailService;
        this.clock = clock;
    }

    public void schedule(GoldThresholdAlertMessage message) {
        if (message == null) {
            return;
        }
        Instant base = Instant.now(clock);
        for (Duration delay : SEND_DELAYS) {
            Instant sendAt = base.plus(delay);
            taskScheduler.schedule(() -> emailService.notifyThresholdAlert(message), sendAt);
        }
        if (log.isInfoEnabled()) {
            log.info("Scheduled {} threshold alert emails for {}", SEND_DELAYS.size(), message.alertTime());
        }
    }
}
