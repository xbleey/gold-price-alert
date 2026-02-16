package com.xbleey.goldpricealert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbleey.goldpricealert.config.GoldProperties;
import com.xbleey.goldpricealert.repository.GoldAlertHistoryStore;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
                configStore(),
                GoldAlertHistoryStore.noop()
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

    private static GoldAlertLevelConfigStore configStore() {
        GoldAlertLevelConfigStore store = mock(GoldAlertLevelConfigStore.class);
        when(store.listLevels()).thenReturn(List.of(
                new GoldAlertLevelConfig("P1", 1, java.math.BigDecimal.valueOf(0.10), 1, 30, true),
                new GoldAlertLevelConfig("P2", 2, java.math.BigDecimal.valueOf(0.30), 5, 15, true),
                new GoldAlertLevelConfig("P3", 3, java.math.BigDecimal.valueOf(0.60), 15, 10, true),
                new GoldAlertLevelConfig("P4", 4, java.math.BigDecimal.ONE, 60, 5, true),
                new GoldAlertLevelConfig("P5", 5, java.math.BigDecimal.valueOf(2.00), 60, 0, true)
        ));
        return store;
    }
}
