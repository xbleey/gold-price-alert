package com.xbleey.goldpricealert.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xbleey.goldpricealert.mapper.GoldMailRecipientMapper;
import com.xbleey.goldpricealert.model.GoldMailRecipient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MyBatisPlusGoldMailRecipientStore implements GoldMailRecipientStore {

    private final GoldMailRecipientMapper mapper;

    public MyBatisPlusGoldMailRecipientStore(GoldMailRecipientMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<GoldMailRecipient> findAll() {
        LambdaQueryWrapper<GoldMailRecipient> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(GoldMailRecipient::getId);
        return mapper.selectList(wrapper);
    }

    @Override
    public List<GoldMailRecipient> findEnabled() {
        LambdaQueryWrapper<GoldMailRecipient> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GoldMailRecipient::getEnabled, true)
                .orderByAsc(GoldMailRecipient::getId);
        return mapper.selectList(wrapper);
    }

    @Override
    public Optional<GoldMailRecipient> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public Optional<GoldMailRecipient> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<GoldMailRecipient> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GoldMailRecipient::getEmail, email.trim())
                .last("limit 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public GoldMailRecipient save(GoldMailRecipient record) {
        mapper.insert(record);
        return record;
    }

    @Override
    public int update(GoldMailRecipient record) {
        return mapper.updateById(record);
    }

    @Override
    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }
}
