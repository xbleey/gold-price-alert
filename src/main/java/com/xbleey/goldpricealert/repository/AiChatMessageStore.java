package com.xbleey.goldpricealert.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xbleey.goldpricealert.model.AiChatMessage;

import java.util.List;

public interface AiChatMessageStore {

    AiChatMessage save(AiChatMessage message);

    List<AiChatMessage> findRecentBySessionIdDesc(String sessionId, int limit);

    Page<AiChatMessage> findBySessionId(String sessionId, long pageNum, long pageSize);
}
