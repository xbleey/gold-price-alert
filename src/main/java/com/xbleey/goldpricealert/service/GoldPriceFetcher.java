package com.xbleey.goldpricealert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbleey.goldpricealert.config.GoldProperties;
import com.xbleey.goldpricealert.model.GoldApiResponse;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class GoldPriceFetcher {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceFetcher.class);
    private static final int MAX_FETCH_ATTEMPTS = 4;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final GoldProperties properties;
    private final GoldPriceHistory history;
    private final GoldAlertEvaluator evaluator;
    private final GoldThresholdAlertEvaluator thresholdEvaluator;
    private final GoldApiStatusMonitor apiStatusMonitor;
    private final FetchRetrySleeper retrySleeper;
    private final Clock clock;

    public GoldPriceFetcher(
            OkHttpClient okHttpClient,
            ObjectMapper objectMapper,
            GoldProperties properties,
            GoldPriceHistory history,
            GoldAlertEvaluator evaluator,
            GoldThresholdAlertEvaluator thresholdEvaluator,
            GoldApiStatusMonitor apiStatusMonitor,
            FetchRetrySleeper retrySleeper,
            Clock clock
    ) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.history = history;
        this.evaluator = evaluator;
        this.thresholdEvaluator = thresholdEvaluator;
        this.apiStatusMonitor = apiStatusMonitor;
        this.retrySleeper = retrySleeper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "#{@goldProperties.fetchInterval.toMillis()}")
    public void fetch() {
        if (!shouldFetchNow()) {
            return;
        }
        fetchOnce();
    }

    public Optional<GoldPriceSnapshot> fetchOnce() {
        Request request = new Request.Builder()
                .url(properties.getApiUrl().toString())
                .get()
                .build();
        String lastFailureDetail = null;
        for (int attempt = 1; attempt <= MAX_FETCH_ATTEMPTS; attempt++) {
            FetchAttemptResult result = executeFetch(request);
            if (result.snapshot() != null) {
                GoldPriceSnapshot snapshot = result.snapshot();
                apiStatusMonitor.recordSuccess();
                boolean stored = history.addIfPriceChanged(snapshot);
                if (!stored) {
                    log.info("Fetched gold price unchanged: {} time:{}, skip persisting", snapshot.price(), result.updatedAtFormatted());
                    return Optional.of(snapshot);
                }
                boolean alerted = evaluator.evaluate(snapshot);
                if (thresholdEvaluator != null) {
                    thresholdEvaluator.evaluate(snapshot);
                }
                if (!alerted) {
                    log.info("Fetched gold price: {} time:{}", snapshot.price(), result.updatedAtFormatted());
                }
                return Optional.of(snapshot);
            }
            lastFailureDetail = result.failureDetail();
            if (attempt == MAX_FETCH_ATTEMPTS) {
                break;
            }
            log.warn(
                    "Failed to fetch gold price on attempt {}/{}, retrying in {}s: {}",
                    attempt,
                    MAX_FETCH_ATTEMPTS,
                    RETRY_DELAY.toSeconds(),
                    lastFailureDetail
            );
            if (!pauseBeforeRetry(attempt)) {
                apiStatusMonitor.recordFailure("Retry interrupted");
                return Optional.empty();
            }
        }
        apiStatusMonitor.recordFailure(lastFailureDetail);
        return Optional.empty();
    }

    private boolean shouldFetchNow() {
        Instant now = Instant.now(clock);
        LocalDate utcDate = now.atZone(ZoneOffset.UTC).toLocalDate();
        if (!isTradingDayUtc(utcDate)) {
            log.debug("Skip fetch: outside trading day (UTC), date={}", utcDate);
            return false;
        }
        return true;
    }

    private FetchAttemptResult executeFetch(Request request) {
        try (Response httpResponse = okHttpClient.newCall(request).execute()) {
            if (!httpResponse.isSuccessful()) {
                String errorDetail = "HTTP status " + httpResponse.code();
                log.warn("Gold API returned http status {}", httpResponse.code());
                return FetchAttemptResult.failure(errorDetail);
            }
            if (httpResponse.body() == null) {
                log.warn("Gold API returned empty body");
                return FetchAttemptResult.failure("Empty response body");
            }
            GoldApiResponse response = objectMapper.readValue(
                    httpResponse.body().byteStream(),
                    GoldApiResponse.class
            );
            Instant fetchedAt = Instant.now(clock);
            GoldPriceSnapshot snapshot = new GoldPriceSnapshot(fetchedAt, response);
            String updatedAtFormatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(clock.getZone())
                    .format(response.updatedAt());
            return FetchAttemptResult.success(snapshot, updatedAtFormatted);
        } catch (Exception ex) {
            log.warn("Failed to fetch gold price", ex);
            return FetchAttemptResult.failure(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private boolean pauseBeforeRetry(int attempt) {
        try {
            retrySleeper.pause(RETRY_DELAY);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Gold price fetch retry interrupted on attempt {}", attempt, ex);
            return false;
        }
    }

    private static boolean isTradingDayUtc(LocalDate utcDate) {
        DayOfWeek dayOfWeek = utcDate.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        return !isLondonHoliday(utcDate);
    }

    private static boolean isLondonHoliday(LocalDate date) {
        int year = date.getYear();
        if (isFixedOrObservedHoliday(date, Month.JANUARY, 1)) {
            return true;
        }
        if (date.equals(observedDate(LocalDate.of(year + 1, Month.JANUARY, 1)))) {
            return true;
        }
        if (isFixedOrObservedHoliday(date, Month.DECEMBER, 25)) {
            return true;
        }
        if (isFixedOrObservedHoliday(date, Month.DECEMBER, 26)) {
            return true;
        }
        LocalDate easterSunday = easterSunday(year);
        return date.equals(easterSunday.minusDays(2)) || date.equals(easterSunday.plusDays(1));
    }

    private static boolean isFixedOrObservedHoliday(LocalDate date, Month month, int dayOfMonth) {
        LocalDate holiday = LocalDate.of(date.getYear(), month, dayOfMonth);
        return date.equals(holiday) || date.equals(observedDate(holiday));
    }

    private static LocalDate observedDate(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> date.minusDays(1);
            case SUNDAY -> date.plusDays(1);
            default -> date;
        };
    }

    private static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    private record FetchAttemptResult(GoldPriceSnapshot snapshot, String updatedAtFormatted, String failureDetail) {

        private static FetchAttemptResult success(GoldPriceSnapshot snapshot, String updatedAtFormatted) {
            return new FetchAttemptResult(snapshot, updatedAtFormatted, null);
        }

        private static FetchAttemptResult failure(String failureDetail) {
            return new FetchAttemptResult(null, null, failureDetail);
        }
    }
}
