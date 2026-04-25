package com.xbleey.goldpricealert.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xbleey.goldpricealert.mapper.AiChatSessionMapper;
import com.xbleey.goldpricealert.model.AiChatSession;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MyBatisPlusAiChatSessionStore implements AiChatSessionStore {

    private final AiChatSessionMapper mapper;

    public MyBatisPlusAiChatSessionStore(AiChatSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AiChatSession save(AiChatSession session) {
        mapper.insert(session);
        return session;
    }

    @Override
    public int update(AiChatSession session) {
        return mapper.updateById(session);
    }

    @Override
    public Optional<AiChatSession> findBySessionIdAndUsername(String sessionId, String username) {
        if (sessionId == null || sessionId.isBlank() || username == null || username.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<AiChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatSession::getSessionId, sessionId.trim())
                .eq(AiChatSession::getUsername, username.trim())
                .last("limit 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public Page<AiChatSession> findByUsername(String username, long pageNum, long pageSize) {
        Page<AiChatSession> page = Page.of(Math.max(1L, pageNum), Math.clamp(pageSize, 1L, 200L));
        LambdaQueryWrapper<AiChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatSession::getUsername, username)
                .orderByDesc(AiChatSession::getUpdatedAt)
                .orderByDesc(AiChatSession::getId);
        return mapper.selectPage(page, wrapper);
    }
}
