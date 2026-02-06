package com.xbleey.goldpricealert;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.xbleey.goldpricealert.mapper")
public class GoldPriceAlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoldPriceAlertApplication.class, args);
    }

}
