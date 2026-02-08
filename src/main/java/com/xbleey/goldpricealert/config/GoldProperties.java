package com.xbleey.goldpricealert.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "gold")
public class GoldProperties {

    private URI apiUrl;
    private Duration fetchInterval;

    @PostConstruct
    public void validate() {
        if (apiUrl == null) {
            throw new IllegalStateException("gold.apiUrl must be configured");
        }
    }
}
