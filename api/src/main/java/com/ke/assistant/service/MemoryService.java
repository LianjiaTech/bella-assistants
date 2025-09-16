package com.ke.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.assistant.db.generated.tables.pojos.MessageDb;
import com.ke.assistant.db.repo.MessageRepo;
import com.ke.assistant.dto.memory.MemoryRequest;
import com.ke.assistant.dto.memory.MemoryResponse;
import com.ke.assistant.enums.MemoryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryService {

    private final MessageRepo messageRepo;
    private final ObjectMapper objectMapper;

    public MemoryResponse queryMemory(MemoryRequest request) {
        MemoryResponse response = new MemoryResponse();

        if ((request.getType() == MemoryType.LONG_MEMORY || request.getType() == MemoryType.MIX_MEMORY) 
            && request.getQuery() != null && !request.getQuery().trim().isEmpty()) {
            response.setLongMemory(new ArrayList<>());
        }

        if (request.getType() == MemoryType.SHORT_MEMORY || request.getType() == MemoryType.MIX_MEMORY) {
            List<MessageDb> messages = getRecentMessages(request.getThreadId(), request.getStrategyParam().getTurnNum() * 2);
            response.setShortMemory(transformMessageContent(messages));
        }

        return response;
    }

    private List<MessageDb> getRecentMessages(String threadId, int limit) {
        List<MessageDb> messages = messageRepo.findRecentByThreadId(threadId, limit);
        Collections.reverse(messages);
        return messages;
    }

    private List<Map<String, Object>> transformMessageContent(List<MessageDb> messages) {
        return messages.stream().map(msg -> {
            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("role", msg.getRole());
            messageMap.put("content", parseAndTransformContent(msg.getContent()));
            messageMap.put("reasoning_content", msg.getReasoningContent());
            return messageMap;
        }).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Object parseAndTransformContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<Map<String, Object>> contentList = objectMapper.readValue(content, List.class);
            for (Map<String, Object> contentItem : contentList) {
                if ("text".equals(contentItem.get("type"))) {
                    Object textObj = contentItem.get("text");
                    if (textObj instanceof Map) {
                        Map<String, Object> textMap = (Map<String, Object>) textObj;
                        contentItem.put("text", textMap.get("value"));
                    }
                }
            }
            return contentList;
        } catch (Exception e) {
            log.warn("Failed to parse message content: {}", content, e);
            return Collections.emptyList();
        }
    }
}