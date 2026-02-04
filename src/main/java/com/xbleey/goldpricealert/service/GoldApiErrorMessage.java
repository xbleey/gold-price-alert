package com.xbleey.goldpricealert.service;

import java.time.Duration;
import java.time.Instant;

public record GoldApiErrorMessage(Instant failureTime, String apiUrl, String errorDetail, Duration downtime) {
}
