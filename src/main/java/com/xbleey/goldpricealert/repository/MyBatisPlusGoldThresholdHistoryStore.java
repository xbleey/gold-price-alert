package com.xbleey.goldpricealert.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xbleey.goldpricealert.mapper.GoldThresholdHistoryMapper;
import com.xbleey.goldpricealert.model.GoldThresholdHistory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Component
public class MyBatisPlusGoldThresholdHistoryStore implements GoldThresholdHistoryStore {

    private final GoldThresholdHistoryMapper mapper;

    public MyBatisPlusGoldThresholdHistoryStore(GoldThresholdHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public GoldThresholdHistory save(GoldThresholdHistory record) {
        mapper.insert(record);
        return record;
    }

    @Override
    public int update(GoldThresholdHistory record) {
        return mapper.updateById(record);
    }

    @Override
    public Optional<GoldThresholdHistory> findLatest() {
        LambdaQueryWrapper<GoldThresholdHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(GoldThresholdHistory::getSetAt)
                .last("limit 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public Optional<GoldThresholdHistory> findLatestPending() {
        LambdaQueryWrapper<GoldThresholdHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GoldThresholdHistory::getStatus, GoldThresholdHistory.STATUS_PENDING)
                .orderByDesc(GoldThresholdHistory::getSetAt)
                .last("limit 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public boolean markTriggered(Long id, Instant triggeredAt, BigDecimal triggeredPrice) {
        if (id == null) {
            return false;
        }
        LambdaUpdateWrapper<GoldThresholdHistory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GoldThresholdHistory::getId, id)
                .eq(GoldThresholdHistory::getStatus, GoldThresholdHistory.STATUS_PENDING)
                .set(GoldThresholdHistory::getStatus, GoldThresholdHistory.STATUS_TRIGGERED)
                .set(GoldThresholdHistory::getTriggeredAt, triggeredAt)
                .set(GoldThresholdHistory::getTriggeredPrice, triggeredPrice);
        return mapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean markCleared(Long id) {
        if (id == null) {
            return false;
        }
        LambdaUpdateWrapper<GoldThresholdHistory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GoldThresholdHistory::getId, id)
                .eq(GoldThresholdHistory::getStatus, GoldThresholdHistory.STATUS_PENDING)
                .set(GoldThresholdHistory::getStatus, GoldThresholdHistory.STATUS_CLEARED);
        return mapper.update(null, wrapper) > 0;
    }
}
