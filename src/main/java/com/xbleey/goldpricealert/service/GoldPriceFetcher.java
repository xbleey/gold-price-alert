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
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class GoldPriceFetcher {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceFetcher.class);

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final GoldProperties properties;
    private final GoldPriceHistory history;
    private final GoldAlertEvaluator evaluator;
    private final Clock clock;

    public GoldPriceFetcher(
            OkHttpClient okHttpClient,
            ObjectMapper objectMapper,
            GoldProperties properties,
            GoldPriceHistory history,
            GoldAlertEvaluator evaluator,
            Clock clock
    ) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.history = history;
        this.evaluator = evaluator;
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
        try {
            try (Response httpResponse = okHttpClient.newCall(request).execute()) {
                if (!httpResponse.isSuccessful()) {
                    log.warn("Gold API returned http status {}", httpResponse.code());
                    return Optional.empty();
                }
                if (httpResponse.body() == null) {
                    log.warn("Gold API returned empty body");
                    return Optional.empty();
                }
                GoldApiResponse response = objectMapper.readValue(
                        httpResponse.body().byteStream(),
                        GoldApiResponse.class
                );
                Instant fetchedAt = Instant.now(clock);
                GoldPriceSnapshot snapshot = new GoldPriceSnapshot(fetchedAt, response);
                history.add(snapshot);
                boolean alerted = evaluator.evaluate(snapshot);
                String updatedAtFormatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(clock.getZone())
                        .format(response.updatedAt());
                if (!alerted) {
                    log.info("Fetched gold price: {} time:{}", response.price(), updatedAtFormatted);
                }
                return Optional.of(snapshot);
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch gold price", ex);
            return Optional.empty();
        }
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
}
