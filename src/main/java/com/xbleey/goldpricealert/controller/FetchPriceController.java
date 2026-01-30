package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import com.xbleey.goldpricealert.service.GoldPriceFetcher;
import com.xbleey.goldpricealert.service.GoldPriceHistory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class FetchPriceController {

    private final GoldPriceFetcher fetcher;
    private final GoldPriceHistory history;

    public FetchPriceController(GoldPriceFetcher fetcher, GoldPriceHistory history) {
        this.fetcher = fetcher;
        this.history = history;
    }

    @GetMapping("/pirce")
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

    @GetMapping("/history")
    public List<GoldPriceSnapshot> history(@RequestParam(name = "length", defaultValue = "100") int length) {
        return history.getRecent(length);
    }
}
