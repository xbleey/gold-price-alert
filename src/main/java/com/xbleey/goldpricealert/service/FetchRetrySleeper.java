package com.xbleey.goldpricealert.service;

import java.time.Duration;

@FunctionalInterface
public interface FetchRetrySleeper {

    void pause(Duration duration) throws InterruptedException;
}
