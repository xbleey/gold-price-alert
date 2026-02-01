package com.xbleey.goldpricealert.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xbleey.goldpricealert.mapper.GoldPriceSnapshotMapper;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class MyBatisPlusGoldPriceSnapshotStore implements GoldPriceSnapshotStore {

    private final GoldPriceSnapshotMapper mapper;

    public MyBatisPlusGoldPriceSnapshotStore(GoldPriceSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public GoldPriceSnapshot save(GoldPriceSnapshot snapshot) {
        mapper.insert(snapshot);
        return snapshot;
    }

    @Override
    public int update(GoldPriceSnapshot snapshot) {
        return mapper.updateById(snapshot);
    }

    @Override
    public int deleteById(Long id) {
        if (id == null) {
            return 0;
        }
        return mapper.deleteById(id);
    }

    @Override
    public Optional<GoldPriceSnapshot> findSnapshotAtOrBefore(Instant target) {
        if (target == null) {
            return Optional.empty();
        }
        LambdaQueryWrapper<GoldPriceSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.le(GoldPriceSnapshot::getFetchedAt, target)
                .orderByDesc(GoldPriceSnapshot::getFetchedAt)
                .last("limit 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public List<GoldPriceSnapshot> findAllAsc() {
        LambdaQueryWrapper<GoldPriceSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(GoldPriceSnapshot::getFetchedAt);
        return List.copyOf(mapper.selectList(wrapper));
    }

    @Override
    public List<GoldPriceSnapshot> findRecentDesc(int limit) {
        int safeLimit = Math.max(0, limit);
        if (safeLimit == 0) {
            return List.of();
        }
        LambdaQueryWrapper<GoldPriceSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(GoldPriceSnapshot::getFetchedAt)
                .last("limit " + safeLimit);
        return List.copyOf(mapper.selectList(wrapper));
    }
}
