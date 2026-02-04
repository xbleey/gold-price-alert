package com.xbleey.goldpricealert;

import com.xbleey.goldpricealert.model.GoldApiResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;

@Tag("manual")
class GoldApiResponseIntegrationTest {

    @Test
    void shouldFetchAndMapGoldApiResponse() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();
        try {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody("""
                            {"name":"gold","price":1934.56,"symbol":"XAU","updatedAt":"2026-01-05T11:59:00Z","updatedAtReadable":"2026-01-05 11:59:00"}
                            """));
            RestClient restClient = RestClient.builder().build();
            GoldApiResponse response = restClient.get()
                    .uri(URI.create(server.url("/price/XAU").toString()))
                    .retrieve()
                    .body(GoldApiResponse.class);

            Assertions.assertNotNull(response, "response should not be null");
            Assertions.assertNotNull(response.name(), "name should not be null");
            Assertions.assertFalse(response.name().isBlank(), "name should not be blank");
            Assertions.assertEquals("XAU", response.symbol(), "symbol should be XAU");
            Assertions.assertTrue(response.price().doubleValue() > 0.0, "price should be positive");
            Assertions.assertNotNull(response.updatedAt(), "updatedAt should not be null");
            Assertions.assertNotNull(response.updatedAtReadable(), "updatedAtReadable should not be null");
        } finally {
            server.shutdown();
        }
    }
}
