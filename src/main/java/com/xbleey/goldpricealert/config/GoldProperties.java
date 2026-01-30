package com.xbleey.goldpricealert.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "gold")
public class GoldProperties {

    private URI apiUrl;
    private Duration fetchInterval;
    private Duration historyWindow;
    private int historyCapacity = 2000;
}
