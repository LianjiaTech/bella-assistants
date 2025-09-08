package com.ke.assistant.core.memory;

import com.google.common.collect.Lists;
import com.ke.assistant.core.run.ExecutionContext;
import com.ke.assistant.util.MessageUtils;
import com.ke.bella.openapi.protocol.completion.CompletionModelProperties;
import com.ke.bella.openapi.utils.TokenCounter;
import com.knuddels.jtokkit.api.EncodingType;
import com.theokanning.openai.completion.chat.AssistantMessage;
import com.theokanning.openai.completion.chat.AssistantMultipleMessage;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatToolCall;
import com.theokanning.openai.completion.chat.MultiMediaContent;
import com.theokanning.openai.completion.chat.ToolMessage;
import com.theokanning.openai.completion.chat.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

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
    @SuppressWarnings("unchecked")
    public void truncate(ExecutionContext context) {

        List<ChatMessage> messages = context.getChatMessages();

        if (messages == null || messages.isEmpty()) {
            return;
        }
        
        // 检查是否需要截断
        if (!needsTruncation(messages, context)) {
            return;
        }
        
        // 获取受保护的消息索引
        Map<Integer, ChatMessage> protectedMessages = getProtectedMessages(messages);
        
        // 构建截断后的消息列表
        int maxTokens = context.getModelProperties().getMax_input_context();

        // 计算受保护消息的token数
        int protectedTokens = MessageUtils.countToken(Lists.newArrayList(protectedMessages.values()));
        int remainingTokens = maxTokens - protectedTokens;

        if (remainingTokens == 0) {
            context.getChatMessages().clear();
            context.getChatMessages().addAll(protectedMessages.values());
            return;
        }


        if (remainingTokens < 0) {
            shortenContext(protectedMessages.values(), protectedTokens, maxTokens, 10);
            context.getChatMessages().clear();
            context.getChatMessages().addAll(protectedMessages.values());
            return;
        }

        // 从候选消息中选择能够放入剩余空间的消息
        // 优先选择较新的消息，反向遍历列表，使用栈结构，后进（列表中排在前面的消息）先出
        Stack<ChatMessage> selectedMessages = new Stack<>();
        int currentTokens = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (!protectedMessages.containsKey(i)) {
                int messageTokens = MessageUtils.countToken(Lists.newArrayList(message));
                if (currentTokens + messageTokens <= remainingTokens) {
                    selectedMessages.add(message);
                    currentTokens += messageTokens;
                }
            } else {
                selectedMessages.push(message);
            }
        }
        List<ChatMessage> finalMessages = buildMessages(selectedMessages);
        context.getChatMessages().clear();
        context.getChatMessages().addAll(finalMessages);
    }

    /**
     * 上下文不足时，可以缩短工具的输出
     */
    private void shortenContext(Collection<ChatMessage> chatMessages, int protectedTokens, int maxTokens, int maxTimes) {
        if(maxTimes == 0) {
            return;
        }
        int needSaveTokens = protectedTokens - maxTokens;
        if(needSaveTokens <= 0) {
            return;
        }
        // 上下文不足时，找到可以处理的消息
        ToolMessage maxToolMessage = null;
        int maxToolContext = 0;
        for(ChatMessage chatMessage : chatMessages) {
            // 如果有多条消息，只保留最后一条（前面的消息是isFinal类型的工具产生）
            if(chatMessage.getRole().equals("assistant")) {
                if(chatMessage instanceof AssistantMultipleMessage) {
                    AssistantMultipleMessage message = (AssistantMultipleMessage) chatMessage;
                    if(message.getContent() instanceof Collection) {
                        List<MultiMediaContent> contents = Lists.newArrayList((Collection<MultiMediaContent>) message.getContent());
                        message.setContent(contents.get(contents.size() - 1).getText());
                    }
                }
            }
            // 找到最长的工具结果进行处理
            if(chatMessage.getRole().equals("tool")) {
                ToolMessage toolMessage = (ToolMessage) chatMessage;
                int tokens = TokenCounter.tokenCount(toolMessage.getContent(), EncodingType.O200K_BASE);
                if(tokens > needSaveTokens && tokens > maxToolContext) {
                    maxToolMessage = toolMessage;
                    maxToolContext = tokens;
                }
            }
        }
        if(maxToolMessage != null) {
            int limit = maxToolContext - needSaveTokens;
            maxToolMessage.setContent(maxToolMessage.getContent().substring(0, limit));
        } else {
            return;
        }
        int tokens = MessageUtils.countToken(Lists.newArrayList(chatMessages));
        shortenContext(chatMessages, tokens, maxTokens, --maxTimes);
    }

    /**
     * 确保消息一定是 user-assistant 或者 user-assistant-tool-assistant 成对出现
     * @param selectedMessages
     * @return
     */
    @SuppressWarnings("unchecked")
    private List<ChatMessage> buildMessages(Stack<ChatMessage> selectedMessages) {
        List<ChatMessage> result = new ArrayList<>();
        Set<String> preToolCallIds = new HashSet<>();
        while (!selectedMessages.isEmpty()) {
            ChatMessage current = selectedMessages.pop();
            if(result.isEmpty()) {
                // 第一条消息
                if(current.getRole().equals("system") || current.getRole().equals("user")) {
                    result.add(current);
                }
            } else {
                if(current.getRole().equals("system")) {
                    continue;
                }
                ChatMessage pre = result.get(result.size() - 1);
                if(current.getRole().equals("user")) {
                    if(pre.getRole().equals("assistant")) {
                        if(!preToolCallIds.isEmpty()) {
                            // 此情况说明，该工具执行的结果丢失，应该删除此工具调用轮次
                            result.remove(result.size() - 1);
                            // 重新加入再次处理
                            selectedMessages.push(current);
                            continue;
                        }
                    } else if(pre.getRole().equals("user")) {
                        // 连续的user message合并为一条消息
                        UserMessage userMessage = (UserMessage) pre;
                        UserMessage curUserMessage = (UserMessage) current;
                        Collection<MultiMediaContent> curContents = new ArrayList<>();
                        if(curUserMessage.getContent() instanceof Collection) {
                            curContents = (Collection<MultiMediaContent>) curUserMessage.getContent();
                        } else {
                            curContents.add(new MultiMediaContent("text", curUserMessage.getTextContent(), null, null, null));
                        }
                        if(userMessage.getContent() instanceof Collection) {
                            ((Collection<MultiMediaContent>) userMessage.getContent()).addAll(curContents);
                        } else {
                            List<MultiMediaContent> contents = new ArrayList<>();
                            contents.add(new MultiMediaContent("text", userMessage.getTextContent(), null, null, null));
                            contents.addAll(curContents);
                            userMessage.setContent(contents);
                        }
                        continue;
                    } else if(pre.getRole().equals("tool")) {
                        // tool之后必须是assistant消息，此时加入user，代表工具后的assistant消息丢失，工具执行的轮次存在但不完整，需要补全
                        // 先补全工具结果
                        if(!preToolCallIds.isEmpty()) {
                            // 补全缺少的工具调用
                            preToolCallIds.forEach(id -> {
                                ToolMessage toolMessage = new ToolMessage();
                                toolMessage.setToolCallId(id);
                                toolMessage.setContent("工具执行结果——已省略");
                                result.add(toolMessage);
                            });
                        }
                        // 再补全工具调用后的助手消息
                        AssistantMessage assistantMessage = new AssistantMessage();
                        assistantMessage.setContent("助手消息——已省略");
                        result.add(assistantMessage);
                    }
                    result.add(current);
                    preToolCallIds = new HashSet<>();
                } else if(current.getRole().equals("assistant")) {
                    // 此情况代表，消息被截断，缺少前序的user/tool消息，不添加此消息
                    if(pre.getRole().equals("system")) {
                        continue;
                    }
                    // 异常情况直接跳过，不添加此助手消息
                    if(pre.getRole().equals("assistant")) {
                        continue;
                    }
                    if(pre.getRole().equals("tool")) {
                        if(!preToolCallIds.isEmpty()) {
                            // 补全缺少的工具调用
                            preToolCallIds.forEach(id -> {
                                ToolMessage toolMessage = new ToolMessage();
                                toolMessage.setToolCallId(id);
                                toolMessage.setContent("工具执行结果——已省略");
                                result.add(toolMessage);
                            });
                        }
                    }
                    result.add(current);
                    preToolCallIds = extractToolCallIds(current);
                } else if(current.getRole().equals("tool")) {
                    ToolMessage toolMessage = (ToolMessage) current;
                    // 此时代表前面的工具调用消息被截断或者工具结果重复，直接跳过，不添加此工具结果
                    if(!preToolCallIds.contains(toolMessage.getToolCallId())) {
                        continue;
                    }
                    preToolCallIds.remove(toolMessage.getToolCallId());
                    result.add(current);
                }
            }
        }
        return result;
    }


    private Set<String> extractToolCallIds(ChatMessage assistantMessage) {
        List<ChatToolCall> toolCalls = new ArrayList<>();
        if(assistantMessage instanceof AssistantMessage) {
            toolCalls = ((AssistantMessage) assistantMessage).getToolCalls();
        }
        if(assistantMessage instanceof AssistantMultipleMessage) {
            toolCalls = ((AssistantMultipleMessage) assistantMessage).getToolCalls();
        }
        if(toolCalls != null) {
            return toolCalls.stream().map(ChatToolCall::getId).collect(Collectors.toSet());
        }
        return new HashSet<>();
    }


    /**
     * 获取保护的消息索引（不应被截断的消息）
     * 默认保护第一条系统消息和最后一条用户消息
     *
     * @param messages 消息列表
     * @return 受保护的消息
     */
    private Map<Integer, ChatMessage> getProtectedMessages(List<ChatMessage> messages) {
        Map<Integer, ChatMessage> protectedMessages = new HashMap<>();

        if (messages == null || messages.isEmpty()) {
            return new HashMap<>();
        }

        // 保护第一条系统消息
        for (int i = 0; i < messages.size(); i++) {
            if ("system".equals(messages.get(i).getRole())) {
                protectedMessages.put(i, messages.get(i));
                break;
            }
        }

        // 保护最后一条用户消息（确保有输入）
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                protectedMessages.put(i, messages.get(i));
                break;
            }
        }

        // 如果最后一条消息是tool，需要保护该条消息和assistant的工具调用消息
        if(messages.get(messages.size() - 1).getRole().equals("tool")) {
            protectedMessages.put(messages.size() - 1, messages.get(messages.size() - 1));
            // 最后一条assistant是要求调用此工具的
            for (int i = messages.size() - 1; i >= 0; i--) {
                if ("assistant".equals(messages.get(i).getRole())) {
                    protectedMessages.put(i, messages.get(i));
                    break;
                }
            }
        }

        return protectedMessages;
    }

    private boolean needsTruncation(List<ChatMessage> messages, ExecutionContext context) {
        CompletionModelProperties properties = context.getModelProperties();
        return MessageUtils.countToken(messages) > properties.getMax_input_context();
    }
}
