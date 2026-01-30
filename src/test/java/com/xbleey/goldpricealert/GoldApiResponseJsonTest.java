package com.xbleey.goldpricealert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xbleey.goldpricealert.model.GoldApiResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GoldApiResponseJsonTest {

    @Test
    void shouldDeserializeGoldApiResponseJson() throws Exception {
        String json = """
                {
                  "name": "Gold",
                  "price": 5095.0,
                  "symbol": "XAU",
                  "updatedAt": "2026-01-30T14:41:02Z",
                  "updatedAtReadable": "a few seconds ago"
                }
                """;

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        GoldApiResponse response = mapper.readValue(json, GoldApiResponse.class);

        Assertions.assertEquals("Gold", response.name());
        Assertions.assertEquals(5095.0, response.price(), 0.0001);
        Assertions.assertEquals("XAU", response.symbol());
        Assertions.assertNotNull(response.updatedAt());
        Assertions.assertEquals("a few seconds ago", response.updatedAtReadable());
    }
}
