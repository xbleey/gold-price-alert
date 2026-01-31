package com.xbleey.goldpricealert.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "gold.alert.mail")
public class GoldAlertMailProperties {

    private String sender;
    private List<String> recipients = new ArrayList<>();
}
