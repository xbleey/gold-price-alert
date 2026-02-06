package com.xbleey.goldpricealert.repository;

import com.xbleey.goldpricealert.model.GoldAlertHistory;

@FunctionalInterface
public interface GoldAlertHistoryStore {

    GoldAlertHistory save(GoldAlertHistory record);

    static GoldAlertHistoryStore noop() {
        return record -> record;
    }
}
