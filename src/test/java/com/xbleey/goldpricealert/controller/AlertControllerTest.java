package com.xbleey.goldpricealert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xbleey.goldpricealert.mapper.GoldAlertHistoryMapper;
import com.xbleey.goldpricealert.model.GoldAlertHistory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertControllerTest {

    @Test
    void listAppliesUpDirectionFilter() {
        initLambdaCache();
        GoldAlertHistoryMapper mapper = mock(GoldAlertHistoryMapper.class);
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AlertController controller = new AlertController(mapper);

        Map<String, Object> response = controller.list(1, 20, List.of("P1"), List.of("up"));

        assertThat(response).containsEntry("current", 1L);
        assertThat(response).containsEntry("pageSize", 20L);

        ArgumentCaptor<LambdaQueryWrapper<GoldAlertHistory>> wrapperCaptor = wrapperCaptor();
        verify(mapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("alert_level")
                .contains("change_percent")
                .contains(">");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs()).containsValue(BigDecimal.ZERO);
    }

    @Test
    void listAppliesDownDirectionFilterForChineseAlias() {
        initLambdaCache();
        GoldAlertHistoryMapper mapper = mock(GoldAlertHistoryMapper.class);
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AlertController controller = new AlertController(mapper);

        controller.list(1, 20, null, List.of("跌"));

        ArgumentCaptor<LambdaQueryWrapper<GoldAlertHistory>> wrapperCaptor = wrapperCaptor();
        verify(mapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("change_percent")
                .contains("<");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs()).containsValue(BigDecimal.ZERO);
    }

    private void initLambdaCache() {
        if (TableInfoHelper.getTableInfo(GoldAlertHistory.class) != null) {
            return;
        }
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, GoldAlertHistory.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<LambdaQueryWrapper<GoldAlertHistory>> wrapperCaptor() {
        return ArgumentCaptor.forClass((Class<LambdaQueryWrapper<GoldAlertHistory>>) (Class<?>) LambdaQueryWrapper.class);
    }
}
