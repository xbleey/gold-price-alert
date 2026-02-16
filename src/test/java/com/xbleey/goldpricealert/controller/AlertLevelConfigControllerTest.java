package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.service.GoldAlertLevelConfig;
import com.xbleey.goldpricealert.service.GoldAlertLevelConfigStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertLevelConfigControllerTest {

    @Test
    void listLevelsReturnsRecords() {
        GoldAlertLevelConfigStore store = mock(GoldAlertLevelConfigStore.class);
        when(store.listLevels()).thenReturn(List.of(
                new GoldAlertLevelConfig("P1", 1, new BigDecimal("0.10"), 1, 30, true),
                new GoldAlertLevelConfig("P6", 6, new BigDecimal("0.20"), 5, 1, false)
        ));
        AlertLevelConfigController controller = new AlertLevelConfigController(store);

        Map<String, Object> response = controller.listLevels();

        assertThat(response).containsEntry("total", 2);
        @SuppressWarnings("unchecked")
        List<GoldAlertLevelConfig> records = (List<GoldAlertLevelConfig>) response.get("records");
        assertThat(records).extracting(GoldAlertLevelConfig::levelName).containsExactly("P1", "P6");
    }

    @Test
    void createLevelReturnsCreated() {
        GoldAlertLevelConfigStore store = mock(GoldAlertLevelConfigStore.class);
        GoldAlertLevelConfig created = new GoldAlertLevelConfig("P6", 6, new BigDecimal("0.20"), 5, 1, false);
        when(store.createLevel("P6", new BigDecimal("0.20"), 5, 1)).thenReturn(created);
        AlertLevelConfigController controller = new AlertLevelConfigController(store);

        ResponseEntity<Map<String, Object>> response = controller.createLevel(
                new AlertLevelConfigController.AlertLevelUpsertRequest("P6", new BigDecimal("0.20"), 5, 1)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("status", "created");
        verify(store).createLevel("P6", new BigDecimal("0.20"), 5, 1);
    }

    @Test
    void deleteProtectedLevelReturnsBadRequest() {
        GoldAlertLevelConfigStore store = mock(GoldAlertLevelConfigStore.class);
        when(store.deleteLevel("P1")).thenThrow(new IllegalArgumentException("P1~P5 are fixed and cannot be deleted"));
        AlertLevelConfigController controller = new AlertLevelConfigController(store);

        ResponseEntity<Map<String, Object>> response = controller.deleteLevel("P1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", "bad_request");
    }
}
