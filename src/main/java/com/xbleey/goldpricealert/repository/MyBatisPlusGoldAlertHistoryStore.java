package com.xbleey.goldpricealert.repository;

import com.xbleey.goldpricealert.mapper.GoldAlertHistoryMapper;
import com.xbleey.goldpricealert.model.GoldAlertHistory;
import org.springframework.stereotype.Component;

@Component
public class MyBatisPlusGoldAlertHistoryStore implements GoldAlertHistoryStore {

    private final GoldAlertHistoryMapper mapper;

    public MyBatisPlusGoldAlertHistoryStore(GoldAlertHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public GoldAlertHistory save(GoldAlertHistory record) {
        mapper.insert(record);
        return record;
    }
}
