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
import java.time.Instant;
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
                evaluator.evaluate(snapshot);
                log.info("Fetched gold price: {} {}", response.price(), response.symbol());
                return Optional.of(snapshot);
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch gold price", ex);
            return Optional.empty();
        }
    }
}
