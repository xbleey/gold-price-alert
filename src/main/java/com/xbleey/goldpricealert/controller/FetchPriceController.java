package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import com.xbleey.goldpricealert.service.GoldPriceFetcher;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FetchPriceController {

    private final GoldPriceFetcher fetcher;

    public FetchPriceController(GoldPriceFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @PostMapping("/pirce")
    public Map<String, Object> triggerFetch() {
        Optional<GoldPriceSnapshot> snapshot = fetcher.fetchOnce();
        if (snapshot.isEmpty()) {
            return Map.of("status", "failed");
        }
        GoldPriceSnapshot value = snapshot.get();
        return Map.of(
                "status", "ok",
                "fetchedAt", value.fetchedAt().toString(),
                "price", value.price(),
                "symbol", value.response().symbol()
        );
    }
}
