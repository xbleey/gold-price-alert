package com.xbleey.goldpricealert.enums;

public enum GoldThresholdDirection {
    UP,
    DOWN;

    public String subjectTag() {
        return this == UP ? "UP TO" : "DOWN TO";
    }
}
