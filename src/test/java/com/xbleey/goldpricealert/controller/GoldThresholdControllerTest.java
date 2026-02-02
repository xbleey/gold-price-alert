package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.service.GoldThresholdStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoldThresholdControllerTest {

    @Test
    void getThresholdReturnsNotSetWhenEmpty() {
        GoldThresholdStore store = mock(GoldThresholdStore.class);
        when(store.getThreshold()).thenReturn(Optional.empty());
        GoldThresholdController controller = new GoldThresholdController(store);

        Map<String, Object> response = controller.getThreshold();

        assertThat(response).containsEntry("status", "not_set");
        assertThat(response).containsEntry("threshold", null);
    }

    @Test
    void getThresholdReturnsValueWhenPresent() {
        GoldThresholdStore store = mock(GoldThresholdStore.class);
        when(store.getThreshold()).thenReturn(Optional.of(new BigDecimal("4500")));
        GoldThresholdController controller = new GoldThresholdController(store);

        Map<String, Object> response = controller.getThreshold();

        assertThat(response).containsEntry("status", "ok");
        assertThat(response).containsEntry("threshold", "4500");
    }

    @Test
    void setThresholdDelegatesToStore() {
        GoldThresholdStore store = mock(GoldThresholdStore.class);
        when(store.setThreshold(new BigDecimal("4500"))).thenReturn(new BigDecimal("4500"));
        GoldThresholdController controller = new GoldThresholdController(store);

        Map<String, Object> response = controller.setThreshold(new BigDecimal("4500"));

        assertThat(response).containsEntry("status", "ok");
        assertThat(response).containsEntry("threshold", "4500");
        verify(store).setThreshold(new BigDecimal("4500"));
    }

    @Test
    void clearThresholdDelegatesToStore() {
        GoldThresholdStore store = mock(GoldThresholdStore.class);
        GoldThresholdController controller = new GoldThresholdController(store);

        Map<String, Object> response = controller.clearThreshold();

        assertThat(response).containsEntry("status", "cleared");
        verify(store).clearThreshold();
    }
}
