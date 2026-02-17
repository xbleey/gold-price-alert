package com.xbleey.goldpricealert.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xbleey.goldpricealert.mapper.AppUserMapper;
import com.xbleey.goldpricealert.model.AppUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class MyBatisPlusAppUserStore implements AppUserStore {

    private final AppUserMapper mapper;

    public MyBatisPlusAppUserStore(AppUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AppUser> findAll() {
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AppUser::getId);
        return mapper.selectList(wrapper);
    }

    @Override
    public Optional<AppUser> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public Optional<AppUser> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppUser::getUsername, username.trim().toLowerCase(Locale.ROOT))
                .last("limit 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public AppUser save(AppUser user) {
        mapper.insert(user);
        return user;
    }

    @Override
    public int update(AppUser user) {
        return mapper.updateById(user);
    }

    @Override
    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }
}
