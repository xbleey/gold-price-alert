package com.xbleey.goldpricealert.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xbleey.goldpricealert.mapper.AiChatMessageMapper;
import com.xbleey.goldpricealert.model.AiChatMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MyBatisPlusAiChatMessageStore implements AiChatMessageStore {

    private final AiChatMessageMapper mapper;

    public MyBatisPlusAiChatMessageStore(AiChatMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AiChatMessage save(AiChatMessage message) {
        mapper.insert(message);
        return message;
    }

    @Override
    public List<AiChatMessage> findRecentBySessionIdDesc(String sessionId, int limit) {
        int safeLimit = Math.max(0, limit);
        if (safeLimit == 0 || sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        LambdaQueryWrapper<AiChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatMessage::getSessionId, sessionId.trim())
                .orderByDesc(AiChatMessage::getCreatedAt)
                .orderByDesc(AiChatMessage::getId)
                .last("limit " + safeLimit);
        return List.copyOf(mapper.selectList(wrapper));
    }

    @Override
    public Page<AiChatMessage> findBySessionId(String sessionId, long pageNum, long pageSize) {
        Page<AiChatMessage> page = Page.of(Math.max(1L, pageNum), Math.clamp(pageSize, 1L, 200L));
        LambdaQueryWrapper<AiChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreatedAt)
                .orderByAsc(AiChatMessage::getId);
        return mapper.selectPage(page, wrapper);
    }
}
