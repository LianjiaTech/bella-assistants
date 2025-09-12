package com.ke.assistant.util;

import com.fasterxml.jackson.databind.node.TextNode;
import com.ke.assistant.core.file.FileInfo;
import com.ke.assistant.db.generated.tables.pojos.MessageDb;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.Renders;
import com.ke.bella.openapi.utils.TokenCounter;
import com.knuddels.jtokkit.api.EncodingType;
import com.theokanning.openai.assistants.assistant.FileSearchRankingOptions;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.message.MessageContent;
import com.theokanning.openai.assistants.message.MessageRequest;
import com.theokanning.openai.assistants.run.ToolCall;
import com.theokanning.openai.assistants.run.ToolCallCodeInterpreter;
import com.theokanning.openai.assistants.run.ToolCallFileSearch;
import com.theokanning.openai.assistants.run.ToolCallFunction;
import com.theokanning.openai.assistants.thread.Attachment;
import com.theokanning.openai.common.LastError;
import com.theokanning.openai.completion.chat.AssistantMessage;
import com.theokanning.openai.completion.chat.AssistantMultipleMessage;
import com.theokanning.openai.completion.chat.ChatFunctionCall;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatToolCall;
import com.theokanning.openai.completion.chat.ImageContent;
import com.theokanning.openai.completion.chat.MultiMediaContent;
import com.theokanning.openai.completion.chat.SystemMessage;
import com.theokanning.openai.completion.chat.ToolMessage;
import com.theokanning.openai.completion.chat.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.C;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Message 工具类
 */
@Slf4j
public class MessageUtils {

    /**
     * 获取消息的源ID
     * 如果消息有copy_message_id元数据，返回copy_message_id；否则返回消息本身的ID
     */
    @SuppressWarnings("unchecked")
    public static String getSourceId(MessageDb message) {
        if (message == null) {
            return null;
        }
        
        // 优先检查是否有copy_message_id
        if (StringUtils.isNotBlank(message.getMetadata())) {
            try {
                Map<String, Object> metadata = JacksonUtils.toMap(message.getMetadata());
                String copyMessageId = (String) metadata.get("copy_message_id");
                if (copyMessageId != null) {
                    return copyMessageId;
                }
            } catch (Exception e) {
                // 如果解析失败，fallback到使用消息ID
            }
        }
        
        // 返回消息本身的ID
        return message.getId();
    }

    /**
     * 获取消息列表中所有的源ID集合
     * 用于重复检测
     */
    public static Set<String> getExistingSourceIds(List<MessageDb> messages) {
        Set<String> existingIds = new HashSet<>();
        
        for (MessageDb message : messages) {
            String sourceId = getSourceId(message);
            if (sourceId != null) {
                existingIds.add(sourceId);
            }
        }
        
        return existingIds;
    }

    /**
     * 检查消息是否已存在（基于源ID）
     */
    public static boolean isMessageExists(MessageDb message, Set<String> existingSourceIds) {
        if (message == null || existingSourceIds == null) {
            return false;
        }
        
        String sourceId = getSourceId(message);
        return sourceId != null && existingSourceIds.contains(sourceId);
    }

    /**
     * 复制消息到指定Thread
     * 处理ID重新生成、线程ID设置、copy_message_id元数据标记
     */
    @SuppressWarnings("unchecked")
    public static MessageDb copyMessageToThread(MessageDb sourceMessage, String targetThreadId) {
        if (sourceMessage == null || targetThreadId == null) {
            return null;
        }

        MessageDb newMessage = new MessageDb();
        BeanUtils.copyProperties(sourceMessage, newMessage);
        newMessage.setId(null); // 重新生成ID
        newMessage.setThreadId(targetThreadId);

        // 在metadata中标记原消息ID
        String metadata = sourceMessage.getMetadata();
        Map<String, Object> metadataMap = new HashMap<>();
        if (StringUtils.isNotBlank(metadata)) {
            try {
                metadataMap = JacksonUtils.toMap(metadata);
            } catch (Exception e) {
                // 如果解析失败，使用空Map
                metadataMap = new HashMap<>();
            }
        }
        metadataMap.put("copy_message_id", sourceMessage.getId());
        newMessage.setMetadata(JacksonUtils.serialize(metadataMap));

        return newMessage;
    }

    /**
     * 格式化消息内容，将内容转换为用于存储的Content格式
     */
    public static List<Object> formatMessageContent(Object content) {
        List<Object> result = new ArrayList<>();

        if(content == null) {
            return result;
        }

        if(content instanceof String) {
            // 字符串内容转换为MessageContentText格式
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");

            Map<String, Object> text = new HashMap<>();
            text.put("value", content);
            text.put("annotations", new ArrayList<>());
            textContent.put("text", text);

            result.add(textContent);
        } else if(content instanceof List) {
            // 列表内容，遍历每个元素
            List<?> contentList = (List<?>) content;
            for (Object item : contentList) {
                if(item instanceof MultiMediaContent) {
                    MultiMediaContent mmContent = (MultiMediaContent) item;

                    if("text".equals(mmContent.getType())) {
                        Map<String, Object> contentItem = new HashMap<>();
                        contentItem.put("type", "text");
                        Map<String, Object> text = new HashMap<>();
                        if(mmContent.getText() != null) {
                            text.put("value", mmContent.getText());
                        }
                        text.put("annotations", new ArrayList<>());
                        contentItem.put("text", text);
                        result.add(contentItem);
                    } else {
                        // 其他类型（image_file, image_url等）直接使用原类型
                        result.add(item);
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported content type.");
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported content type.");
        }

        return result;
    }

    /**
     * 格式化消息内容，将存储格式转换为用于chat completion的Content格式
     */
    public static Object formatChatCompletionContent(List<MessageContent> contents, String role, List<Attachment> attachments, Map<String, FileInfo> fileInfoMap, boolean supportVision) {

        if(contents == null || contents.isEmpty()) {
            return "";
        }

        String attachInfo = "";
        if(attachments != null && !attachments.isEmpty()) {
            List<FileInfo> fileInfos = new ArrayList<>();
            attachments.forEach(attachment -> {
                if(fileInfoMap.containsKey(attachment.getFileId())) {
                    fileInfos.add(fileInfoMap.get(attachment.getFileId()));
                }
            });
            if(!fileInfos.isEmpty()) {
                Map<String, Object> filesMap = new HashMap<>();
                filesMap.put("files", fileInfos);
                attachInfo = Renders.render("templates/attachments.pebble", filesMap);
            }
        }

        if(contents.size() == 1) {
            if("text".equals(contents.get(0).getType())) {
                return contents.get(0).getText().getValue() + attachInfo;
            }
        }

        List<MultiMediaContent> result = new ArrayList<>();

        for(MessageContent content : contents) {
            MultiMediaContent mmContent = new MultiMediaContent();
            if("text".equals(content.getType())) {
                mmContent.setType("text");
                mmContent.setText(content.getText().getValue() + attachInfo);
                // 只在一条消息中添加即可
                attachInfo = "";
            } else {
                // 目前的chat completion，只支持多模态输入，不支持多模态输出
                if(role.equals("user") && supportVision) {
                    mmContent.setType(content.getType());
                    mmContent.setImageFile(content.getImageFile());
                    mmContent.setImageUrl(content.getImageUrl());
                } else {
                    mmContent.setType("text");
                    StringBuilder sb = new StringBuilder();
                    sb.append("消息内容为一张图片，图片信息为：");
                    if(content.getImageFile() != null) {
                        sb.append(content.getImageFile().toString());
                    } else {
                        sb.append(content.getImageUrl().toString());
                    }
                    mmContent.setText(sb.toString());
                }
            }

            result.add(mmContent);
        }

        return result;
    }

    /**
     * 格式化消息，将内容转换为用于chat completion的Message
     */
    public static ChatMessage formatChatCompletionMessage(Message messageInfo, Map<String, FileInfo> fileInfoMap, boolean supportVision, boolean supportReasoningInput) {

        if (messageInfo == null || messageInfo.getRole() == null) {
            return null;
        }
        
        Object content = formatChatCompletionContent(messageInfo.getContent(), messageInfo.getRole(), messageInfo.getAttachments(), fileInfoMap, supportVision);

        switch (messageInfo.getRole()) {
        case "user":
            UserMessage userMessage = new UserMessage();
            userMessage.setContent(content);
            return userMessage;
        case "assistant":
            AssistantMultipleMessage message = new AssistantMultipleMessage(content);
            if(supportReasoningInput) {
                if(message.getReasoningContent() != null && !message.getReasoningContent().isEmpty()) {
                    message.setReasoningContent(message.getReasoningContent());
                }
                if(messageInfo.getMetadata() != null && !messageInfo.getMetadata().isEmpty()) {
                    if(messageInfo.getMetadata().containsKey(MetaConstants.REASONING_SIG)) {
                        message.setReasoningContentSignature(messageInfo.getMetadata().get(MetaConstants.REASONING_SIG));
                    }
                    if(messageInfo.getMetadata().containsKey(MetaConstants.REDACTED_REASONING)) {
                        message.setRedactedReasoningContent(messageInfo.getMetadata().get(MetaConstants.REDACTED_REASONING));
                    }
                }
            }
            return message;
        case "system":
            return new SystemMessage((String) content);
        default:
            log.warn("Unknown message role: {}", messageInfo.getRole());
            return null;
        }
    }

    /**
     * 识别消息类型
     * 基于内容类型判断消息的类别
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String recognizeMessageType(Object content) {
        if (content == null) {
            return "text";
        }

        if(content instanceof String) {
            return "text";
        }

        if(content instanceof List) {
            List<String> contentTypes = new ArrayList<>();
            for (Object item : (List)content) {
                if(item instanceof Map) {
                    Map<String, Object> contentMap = (Map<String, Object>) item;
                    String type = (String) contentMap.get("type");
                    if(type != null) {
                        contentTypes.add(type);
                    }
                } else if(item instanceof MultiMediaContent) {
                    MultiMediaContent mmContent = (MultiMediaContent) item;
                    contentTypes.add(mmContent.getType());
                } else if(item instanceof MessageContent) {
                    MessageContent messageContent = (MessageContent) item;
                    contentTypes.add(messageContent.getType());
                }
            }

            if(contentTypes.isEmpty()) {
                return "text";
            }

            // 检查是否所有内容都是图片类型
            if(contentTypes.stream().allMatch(type -> "image_file".equals(type) || "image_url".equals(type))) {
                return "image";
            }
            // 检查是否所有内容都是文本类型
            else if(contentTypes.stream().allMatch("text"::equals)) {
                return "text";
            }
            // 检查是否所有内容都是音频类型
            else if(contentTypes.stream().allMatch("audio_url"::equals)) {
                return "audio";
            }
            // 检查是否所有内容都是命令类型
            else if(contentTypes.stream().allMatch("clear"::equals)) {
                return "command";
            }
            // 包含多种 content type
            else {
                return "mixed";
            }
        } else {
            throw new IllegalArgumentException("Unsupported content type.");
        }
    }

    public static ToolCall convertToolCall(ChatToolCall chatToolCall) {
        if("code_interpreter".equals(chatToolCall.getFunction().getName())) {
            return ToolCall.builder()
                    .id(chatToolCall.getId())
                    .index(chatToolCall.getIndex())
                    .type("code_interpreter")
                    .codeInterpreter(ToolCallCodeInterpreter.builder()
                            .input(chatToolCall.getFunction().getArguments().asText())
                            .outputs(null) // 初始创建时不包含output
                            .build())
                    .build();
        } else if("file_search".equals(chatToolCall.getFunction().getName())) {
            return ToolCall.builder()
                    .id(chatToolCall.getId())
                    .index(chatToolCall.getIndex())
                    .type("file_search")
                    .fileSearch(ToolCallFileSearch.builder()
                            .rankingOptions(JacksonUtils.deserialize(chatToolCall.getFunction().getArguments().asText(), FileSearchRankingOptions.class))
                            .results(null).build())
                    .build();
        } else {
            return ToolCall.builder()
                    .id(chatToolCall.getId())
                    .index(chatToolCall.getIndex())
                    .type("function")
                    .function(ToolCallFunction.builder()
                            .name(chatToolCall.getFunction().getName())
                            .arguments(chatToolCall.getFunction().getArguments())
                            .output(null) // 初始创建时不包含output
                            .build())
                    .build();
        }
    }

    public static ToolCall convertFunctionToolCall(ChatToolCall chatToolCall) {
        return ToolCall.builder()
                .id(chatToolCall.getId())
                .type("function")
                .function(ToolCallFunction.builder()
                        .name(chatToolCall.getFunction().getName())
                        .arguments(chatToolCall.getFunction().getArguments())
                        .output(null) // 初始创建时不包含output
                        .build())
                .build();
    }

    public static List<ChatMessage> convertToolCallMessages(List<ToolCall> toolCalls, LastError lastError, Map<String, String> metaData, boolean supportReasoningInput) {
        List<ChatMessage> result = new ArrayList<>();
        AssistantMessage toolCallMessage = new AssistantMessage();
        List<ChatToolCall> chatToolCalls = new ArrayList<>();
        List<ToolMessage> toolResultMessages = new ArrayList<>();
        for(ToolCall toolCall : toolCalls) {
            ToolMessage toolResultMessage = new ToolMessage();
            ChatToolCall chatToolCall = new ChatToolCall();
            chatToolCall.setId(toolCall.getId());
            chatToolCall.setIndex(toolCall.getIndex());
            chatToolCall.setType("function");
            ChatFunctionCall function = new ChatFunctionCall();
            toolResultMessage.setToolCallId(chatToolCall.getId());
            if(toolCall.getCodeInterpreter() != null) {
                function.setName("code_interpreter");
                function.setArguments(new TextNode(toolCall.getCodeInterpreter().getInput()));
                if(toolCall.getCodeInterpreter().getOutputs() != null) {
                    toolResultMessage.setContent(JacksonUtils.serialize(toolCall.getCodeInterpreter().getOutputs()));
                }
            } else if(toolCall.getFileSearch() != null) {
                if(toolCall.getCodeInterpreter() != null) {
                    function.setName("file_search");
                    function.setArguments(JacksonUtils.toJsonNode(toolCall.getFileSearch().getRankingOptions()));
                    if(toolCall.getFileSearch().getResults() != null) {
                        toolResultMessage.setContent(JacksonUtils.serialize(toolCall.getFileSearch().getResults()));
                    }
                }
            } else {
                function.setName(toolCall.getFunction().getName());
                function.setArguments(toolCall.getFunction().getArguments());
                if(toolCall.getFunction().getOutput() != null) {
                    toolResultMessage.setContent(toolCall.getFunction().getOutput());
                }
            }
            if(StringUtils.isBlank(toolResultMessage.getContent())) {
                if(lastError != null) {
                    toolResultMessage.setContent(JacksonUtils.serialize(lastError));
                } else {
                    toolResultMessage.setContent("tool call output is null");
                }
            }
            chatToolCall.setFunction(function);
            chatToolCalls.add(chatToolCall);
            toolResultMessages.add(toolResultMessage);
        }
        toolCallMessage.setToolCalls(chatToolCalls);
        if(supportReasoningInput && metaData != null && !metaData.isEmpty()) {
            if(metaData.containsKey(MetaConstants.TEXT)) {
                toolCallMessage.setContent(metaData.get(MetaConstants.TEXT));
            }
            if(metaData.containsKey(MetaConstants.REASONING)) {
                toolCallMessage.setReasoningContent(metaData.get(MetaConstants.REASONING));
            }
            if(metaData.containsKey(MetaConstants.REASONING_SIG)) {
                toolCallMessage.setReasoningContentSignature(metaData.get(MetaConstants.REASONING_SIG));
            }
            if(metaData.containsKey(MetaConstants.REDACTED_REASONING)) {
                toolCallMessage.setRedactedReasoningContent(metaData.get(MetaConstants.REDACTED_REASONING));
            }
        }
        result.add(toolCallMessage);
        result.addAll(toolResultMessages);
        return result;
    }

    public static List<Attachment> getAttachments(List<MessageRequest> messageRequests) {
        if(messageRequests == null || messageRequests.isEmpty()) {
            return new ArrayList<>();
        }
        return messageRequests.stream().map(MessageRequest::getAttachments).flatMap(List::stream).collect(Collectors.toList());
    }

    public static Integer countToken(List<ChatMessage> messages) {
        Integer tokens = 0;
        for(ChatMessage message : messages) {
            if(message instanceof UserMessage) {
                UserMessage userMessage = (UserMessage) message;
                if(userMessage.getContent() == null) {
                    continue;
                }
                if(userMessage.getContent() instanceof String) {
                    tokens += TokenCounter.tokenCount((String) userMessage.getContent(), EncodingType.O200K_BASE);
                } else if(userMessage.getContent() instanceof Collection) {
                    Collection<?> collections = (Collection<?>) ((UserMessage) message).getContent();
                    for(Object content : collections) {
                        if(content instanceof MultiMediaContent) {
                            MultiMediaContent mmContent = (MultiMediaContent) content;
                            if(mmContent.getType().equals("text")) {
                                tokens += TokenCounter.tokenCount(mmContent.getText(), EncodingType.O200K_BASE);
                            } else {
                                tokens += TokenCounter.imageToken(1024, 1024, false);
                            }
                        }
                    }
                }
            } else {
                tokens += TokenCounter.tokenCount(message.getTextContent(), EncodingType.O200K_BASE);
            }
        }
        return tokens;
    }

}
