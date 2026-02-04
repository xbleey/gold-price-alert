package com.xbleey.goldpricealert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbleey.goldpricealert.config.GoldAlertWindowProperties;
import com.xbleey.goldpricealert.config.GoldProperties;
import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import com.xbleey.goldpricealert.support.InMemoryGoldPriceSnapshotStore;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GoldPriceFetcherTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void fetchesDuringTradingDay() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-05T12:00:00Z"), ZoneOffset.UTC);
        FetcherFixture fixture = newFetcher(clock);
        GoldPriceFetcher fetcher = fixture.fetcher;

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"name":"gold","price":1934.56,"symbol":"XAU","updatedAt":"2026-01-05T11:59:00Z","updatedAtReadable":"2026-01-05 11:59:00"}
                        """));

        fetcher.fetch();

        assertThat(server.getRequestCount()).isEqualTo(1);
        assertThat(fixture.history.getAll()).hasSize(1);
    }

    @Test
    void skipsOnWeekend() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-04T12:00:00Z"), ZoneOffset.UTC);
        FetcherFixture fixture = newFetcher(clock);
        GoldPriceFetcher fetcher = fixture.fetcher;

        server.enqueue(new MockResponse().setResponseCode(200));

        fetcher.fetch();

        assertThat(server.getRequestCount()).isZero();
        assertThat(fixture.history.getAll()).isEmpty();
    }

    @Test
    void skipsOnGoodFriday() {
        Clock clock = Clock.fixed(Instant.parse("2026-04-03T12:00:00Z"), ZoneOffset.UTC);
        FetcherFixture fixture = newFetcher(clock);
        GoldPriceFetcher fetcher = fixture.fetcher;

        server.enqueue(new MockResponse().setResponseCode(200));

        fetcher.fetch();

        assertThat(server.getRequestCount()).isZero();
        assertThat(fixture.history.getAll()).isEmpty();
    }

    private FetcherFixture newFetcher(Clock clock) {
        GoldProperties properties = new GoldProperties();
        properties.setApiUrl(server.url("/").uri());
        properties.setFetchInterval(Duration.ofSeconds(5));

        GoldPriceHistory history = new GoldPriceHistory(new InMemoryGoldPriceSnapshotStore());
        GoldAlertEvaluator evaluator = new GoldAlertEvaluator(
                history,
                clock,
                GoldAlertNotifier.noop(),
                windowProperties()
        );
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        GoldPriceFetcher fetcher = new GoldPriceFetcher(
                new OkHttpClient(),
                objectMapper,
                properties,
                history,
                evaluator,
                null,
                mock(GoldApiStatusMonitor.class),
                clock
        );
        return new FetcherFixture(fetcher, history);
    }

    private record FetcherFixture(GoldPriceFetcher fetcher, GoldPriceHistory history) {
    }

    private static GoldAlertWindowProperties windowProperties() {
        GoldAlertWindowProperties properties = new GoldAlertWindowProperties();
        Map<GoldAlertLevel, Duration> levels = new EnumMap<>(GoldAlertLevel.class);
        levels.put(GoldAlertLevel.INFO_LEVEL, Duration.ofMinutes(1));
        levels.put(GoldAlertLevel.MINOR_LEVEL, Duration.ofMinutes(5));
        levels.put(GoldAlertLevel.MODERATE_LEVEL, Duration.ofMinutes(15));
        levels.put(GoldAlertLevel.MAJOR_LEVEL, Duration.ofMinutes(60));
        levels.put(GoldAlertLevel.CRITICAL_LEVEL, Duration.ofMinutes(60));
        properties.setLevels(levels);
        return properties;
    }
}
