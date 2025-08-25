package com.ke.assistant.core.memory.impl;

import com.ke.assistant.core.memory.ContextTruncator;
import com.ke.assistant.core.memory.MemoryStrategy;
import com.theokanning.openai.completion.chat.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * memory管理实现
 * 基于token数量的简单截断策略
 */
@Component
public class DefaultMemoryStrategy implements MemoryStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultMemoryStrategy.class);
    
    @Autowired
    private ContextTruncator contextTruncator;
    
    @Override
    public List<ChatMessage> truncateMessages(List<ChatMessage> messages, int maxTokens, String model) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        
        logger.debug("Truncating messages with MockMemory strategy: {} messages, max tokens: {}", 
            messages.size(), maxTokens);
        
        // 检查是否需要截断
        if (!needsTruncation(messages, maxTokens, model)) {
            logger.debug("No truncation needed");
            return new ArrayList<>(messages);
        }
        
        // 使用基于token的截断策略
        List<ChatMessage> truncated = contextTruncator.truncateByTokens(messages, maxTokens, this, model);
        
        // 验证截断结果
        int finalTokens = estimateTokens(truncated, model);
        if (finalTokens > maxTokens) {
            logger.warn("Truncation result still exceeds max tokens: {} > {}", finalTokens, maxTokens);
            // 如果还是超出，使用更激进的窗口截断
            int windowSize = Math.max(1, messages.size() / 2);
            truncated = contextTruncator.truncateByWindow(messages, windowSize);
        }
        
        logger.debug("Truncation completed: {} -> {} messages, {} tokens", 
            messages.size(), truncated.size(), estimateTokens(truncated, model));
        
        return truncated;
    }
    
    @Override
    public int estimateTokens(List<ChatMessage> messages, String model) {
        return 128000;
    }
    
    @Override
    public Set<Integer> getProtectedMessageIndices(List<ChatMessage> messages) {
        Set<Integer> protectedIndices = new HashSet<>();
        
        if (messages == null || messages.isEmpty()) {
            return protectedIndices;
        }
        
        // 保护所有系统消息
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if ("system".equals(message.getRole())) {
                protectedIndices.add(i);
            }
        }
        
        // 保护最后一条用户消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                protectedIndices.add(i);
                break;
            }
        }
        
        // 保护最后一个完整的对话轮次
        protectLastConversationTurn(messages, protectedIndices);
        
        return protectedIndices;
    }
    
    /**
     * 保护最后一个完整的对话轮次
     */
    private void protectLastConversationTurn(List<ChatMessage> messages, Set<Integer> protectedIndices) {
        // 从最后开始查找最后一个用户消息，然后保护到下一个用户消息之前的所有消息
        int lastUserIndex = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                lastUserIndex = i;
                break;
            }
        }
        
        if (lastUserIndex != -1) {
            // 保护从最后用户消息到结尾的所有消息
            for (int i = lastUserIndex; i < messages.size(); i++) {
                protectedIndices.add(i);
            }
        }
    }

    
    @Override
    public boolean needsTruncation(List<ChatMessage> messages, int maxTokens, String model) {
        return estimateTokens(messages, model) > maxTokens;
    }


}
