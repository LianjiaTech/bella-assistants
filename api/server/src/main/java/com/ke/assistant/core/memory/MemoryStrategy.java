package com.ke.assistant.core.memory;

import com.theokanning.openai.completion.chat.ChatMessage;
import java.util.List;

/**
 * 内存管理策略接口
 * 负责在LLM上下文长度限制下进行消息截断和优化
 */
public interface MemoryStrategy {
    
    /**
     * 整合和截断消息上下文
     * 
     * @param messages 原始消息列表
     * @param maxTokens 最大token限制
     * @param model 使用的模型名称
     * @return 截断后的消息列表
     */
    List<ChatMessage> truncateMessages(List<ChatMessage> messages, int maxTokens, String model);
    
    /**
     * 估算消息的token数量
     * 
     * @param messages 消息列表
     * @param model 使用的模型名称
     * @return 估算的token数量
     */
    int estimateTokens(List<ChatMessage> messages, String model);
    
    /**
     * 检查是否需要截断
     * 
     * @param messages 消息列表
     * @param maxTokens 最大token限制
     * @param model 模型名称
     * @return 是否需要截断
     */
    default boolean needsTruncation(List<ChatMessage> messages, int maxTokens, String model) {
        return estimateTokens(messages, model) > maxTokens;
    }
    
    /**
     * 获取保护的消息索引（不应被截断的消息）
     * 默认保护第一条系统消息和最后一条用户消息
     * 
     * @param messages 消息列表
     * @return 受保护的消息索引集合
     */
    default java.util.Set<Integer> getProtectedMessageIndices(List<ChatMessage> messages) {
        java.util.Set<Integer> protected_indices = new java.util.HashSet<>();
        
        if (messages == null || messages.isEmpty()) {
            return protected_indices;
        }
        
        // 保护第一条系统消息
        for (int i = 0; i < messages.size(); i++) {
            if ("system".equals(messages.get(i).getRole())) {
                protected_indices.add(i);
                break;
            }
        }
        
        // 保护最后一条用户消息（确保有输入）
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                protected_indices.add(i);
                break;
            }
        }
        
        return protected_indices;
    }
}
