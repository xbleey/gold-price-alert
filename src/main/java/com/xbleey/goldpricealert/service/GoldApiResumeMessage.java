package com.xbleey.goldpricealert.service;

import java.time.Duration;
import java.time.Instant;

public record GoldApiResumeMessage(Instant resumeTime, Instant firstFailureTime, Duration downtime, String apiUrl) {
}
