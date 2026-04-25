package com.xbleey.goldpricealert.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xbleey.goldpricealert.model.AiChatSession;

import java.util.Optional;

public interface AiChatSessionStore {

    AiChatSession save(AiChatSession session);

    int update(AiChatSession session);

    Optional<AiChatSession> findBySessionIdAndUsername(String sessionId, String username);

    Page<AiChatSession> findByUsername(String username, long pageNum, long pageSize);
}
