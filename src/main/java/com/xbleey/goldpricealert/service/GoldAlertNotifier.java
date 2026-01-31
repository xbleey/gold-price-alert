package com.xbleey.goldpricealert.service;

@FunctionalInterface
public interface GoldAlertNotifier {

    void notifyAlert(GoldAlertMessage message);

    static GoldAlertNotifier noop() {
        return message -> {
        };
    }
}
