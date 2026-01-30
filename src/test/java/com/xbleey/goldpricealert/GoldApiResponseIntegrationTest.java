package com.xbleey.goldpricealert;

import com.xbleey.goldpricealert.model.GoldApiResponse;
import java.net.URI;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GoldApiResponseIntegrationTest {

    @Test
    void shouldFetchAndMapGoldApiResponse() {
        RestClient restClient = RestClient.builder().build();
        GoldApiResponse response = restClient.get()
                .uri(URI.create("https://api.gold-api.com/price/XAU"))
                .retrieve()
                .body(GoldApiResponse.class);
        System.out.println(response);

        Assertions.assertNotNull(response, "response should not be null");
        Assertions.assertNotNull(response.name(), "name should not be null");
        Assertions.assertFalse(response.name().isBlank(), "name should not be blank");
        Assertions.assertEquals("XAU", response.symbol(), "symbol should be XAU");
        Assertions.assertTrue(response.price() > 0.0, "price should be positive");
        Assertions.assertNotNull(response.updatedAt(), "updatedAt should not be null");
        Assertions.assertNotNull(response.updatedAtReadable(), "updatedAtReadable should not be null");
    }
}
