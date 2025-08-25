package com.ke.assistant.core.memory;

import com.theokanning.openai.completion.chat.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 上下文截断器
 * 提供各种截断策略的具体实现
 */
@Component
public class ContextTruncator {
    
    private static final Logger logger = LoggerFactory.getLogger(ContextTruncator.class);
    
    /**
     * 基于token数量的截断
     * 从中间开始删除消息，保护重要的系统消息和最新的用户消息
     */
    public List<ChatMessage> truncateByTokens(List<ChatMessage> messages, int maxTokens, 
                                             MemoryStrategy strategy, String model) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 检查是否需要截断
        if (!strategy.needsTruncation(messages, maxTokens, model)) {
            return new ArrayList<>(messages);
        }
        
        logger.debug("Truncating {} messages, max tokens: {}", messages.size(), maxTokens);
        
        // 获取受保护的消息索引
        Set<Integer> protectedIndices = strategy.getProtectedMessageIndices(messages);
        
        // 构建截断后的消息列表
        List<ChatMessage> truncatedMessages = new ArrayList<>();
        List<ChatMessage> candidateMessages = new ArrayList<>();
        
        // 首先添加受保护的消息
        for (int i = 0; i < messages.size(); i++) {
            if (protectedIndices.contains(i)) {
                truncatedMessages.add(messages.get(i));
            } else {
                candidateMessages.add(messages.get(i));
            }
        }
        
        // 计算受保护消息的token数
        int protectedTokens = strategy.estimateTokens(truncatedMessages, model);
        int remainingTokens = maxTokens - protectedTokens;
        
        if (remainingTokens <= 0) {
            logger.warn("Protected messages exceed max tokens limit");
            return truncatedMessages;
        }
        
        // 从候选消息中选择能够放入剩余空间的消息
        // 优先选择较新的消息
        Collections.reverse(candidateMessages);
        
        List<ChatMessage> selectedMessages = new ArrayList<>();
        int currentTokens = 0;
        
        for (ChatMessage message : candidateMessages) {
            int messageTokens = strategy.estimateTokens(Arrays.asList(message), model);
            if (currentTokens + messageTokens <= remainingTokens) {
                selectedMessages.add(message);
                currentTokens += messageTokens;
            }
        }
        
        // 恢复消息顺序并合并
        Collections.reverse(selectedMessages);
        List<ChatMessage> result = mergeMessages(truncatedMessages, selectedMessages, messages);
        
        logger.debug("Truncated from {} to {} messages, estimated tokens: {}", 
            messages.size(), result.size(), strategy.estimateTokens(result, model));
        
        return result;
    }
    
    /**
     * 基于消息对的截断
     * 删除完整的用户-助手消息对，保持对话的完整性
     */
    public List<ChatMessage> truncateByMessagePairs(List<ChatMessage> messages, int maxTokens,
                                                   MemoryStrategy strategy, String model) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (!strategy.needsTruncation(messages, maxTokens, model)) {
            return new ArrayList<>(messages);
        }
        
        logger.debug("Truncating {} messages by pairs, max tokens: {}", messages.size(), maxTokens);
        
        // 识别消息对
        List<MessagePair> pairs = identifyMessagePairs(messages);
        List<ChatMessage> systemMessages = extractSystemMessages(messages);
        
        // 计算系统消息的token数
        int systemTokens = strategy.estimateTokens(systemMessages, model);
        int remainingTokens = maxTokens - systemTokens;
        
        if (remainingTokens <= 0) {
            logger.warn("System messages exceed max tokens limit");
            return systemMessages;
        }
        
        // 从最新的消息对开始选择
        Collections.reverse(pairs);
        
        List<ChatMessage> selectedMessages = new ArrayList<>(systemMessages);
        List<MessagePair> selectedPairs = new ArrayList<>();
        int currentTokens = 0;
        
        for (MessagePair pair : pairs) {
            int pairTokens = strategy.estimateTokens(pair.getMessages(), model);
            if (currentTokens + pairTokens <= remainingTokens) {
                selectedPairs.add(pair);
                currentTokens += pairTokens;
            }
        }
        
        // 恢复顺序并添加选中的消息对
        Collections.reverse(selectedPairs);
        for (MessagePair pair : selectedPairs) {
            selectedMessages.addAll(pair.getMessages());
        }
        
        logger.debug("Truncated to {} message pairs, estimated tokens: {}", 
            selectedPairs.size(), strategy.estimateTokens(selectedMessages, model));
        
        return selectedMessages;
    }
    
    /**
     * 滑动窗口截断
     * 保持最近的N条消息
     */
    public List<ChatMessage> truncateByWindow(List<ChatMessage> messages, int windowSize) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (messages.size() <= windowSize) {
            return new ArrayList<>(messages);
        }
        
        logger.debug("Truncating {} messages to window size: {}", messages.size(), windowSize);
        
        // 保护系统消息
        List<ChatMessage> systemMessages = extractSystemMessages(messages);
        List<ChatMessage> otherMessages = messages.stream()
                .filter(msg -> !"system".equals(msg.getRole()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        int availableSlots = windowSize - systemMessages.size();
        if (availableSlots <= 0) {
            return systemMessages;
        }
        
        // 取最新的消息
        List<ChatMessage> recentMessages = otherMessages.subList(
            Math.max(0, otherMessages.size() - availableSlots), 
            otherMessages.size()
        );
        
        List<ChatMessage> result = new ArrayList<>(systemMessages);
        result.addAll(recentMessages);
        
        logger.debug("Truncated to {} messages using window approach", result.size());
        
        return result;
    }
    
    /**
     * 提取系统消息
     */
    private List<ChatMessage> extractSystemMessages(List<ChatMessage> messages) {
        return messages.stream()
                .filter(msg -> "system".equals(msg.getRole()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * 识别消息对（用户消息 + 紧跟的助手消息）
     */
    private List<MessagePair> identifyMessagePairs(List<ChatMessage> messages) {
        List<MessagePair> pairs = new ArrayList<>();
        
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage current = messages.get(i);
            if ("user".equals(current.getRole())) {
                List<ChatMessage> pairMessages = new ArrayList<>();
                pairMessages.add(current);
                
                // 查找紧跟的助手消息和工具消息
                for (int j = i + 1; j < messages.size(); j++) {
                    ChatMessage next = messages.get(j);
                    if ("assistant".equals(next.getRole()) || "tool".equals(next.getRole())) {
                        pairMessages.add(next);
                    } else if ("user".equals(next.getRole())) {
                        break; // 遇到下一个用户消息，当前对结束
                    }
                }
                
                pairs.add(new MessagePair(pairMessages, i));
            }
        }
        
        return pairs;
    }
    
    /**
     * 合并受保护消息和选中消息，保持原始顺序
     */
    private List<ChatMessage> mergeMessages(List<ChatMessage> protectedMessages, 
                                          List<ChatMessage> selectedMessages,
                                          List<ChatMessage> originalMessages) {
        List<ChatMessage> result = new ArrayList<>();
        Set<ChatMessage> messageSet = new HashSet<>(protectedMessages);
        messageSet.addAll(selectedMessages);
        
        for (ChatMessage message : originalMessages) {
            if (messageSet.contains(message)) {
                result.add(message);
            }
        }
        
        return result;
    }
    
    /**
     * 消息对数据结构
     */
    private static class MessagePair {
        private final List<ChatMessage> messages;
        private final int startIndex;
        
        public MessagePair(List<ChatMessage> messages, int startIndex) {
            this.messages = messages;
            this.startIndex = startIndex;
        }
        
        public List<ChatMessage> getMessages() {
            return messages;
        }
        
        public int getStartIndex() {
            return startIndex;
        }
    }
}