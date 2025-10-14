package com.ke.assistant.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ke.assistant.core.file.FileInfo;
import com.ke.assistant.db.generated.tables.pojos.MessageDb;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.Renders;
import com.ke.bella.openapi.utils.TokenCounter;
import com.knuddels.jtokkit.api.EncodingType;
import com.theokanning.openai.assistants.assistant.FileSearchRankingOptions;
import com.theokanning.openai.assistants.message.IncompleteDetails;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.message.MessageContent;
import com.theokanning.openai.assistants.message.MessageRequest;
import com.theokanning.openai.assistants.message.content.AudioData;
import com.theokanning.openai.assistants.message.content.Text;
import com.theokanning.openai.assistants.run.ToolCall;
import com.theokanning.openai.assistants.run.ToolCallCodeInterpreter;
import com.theokanning.openai.assistants.run.ToolCallFileSearch;
import com.theokanning.openai.assistants.run.ToolCallFunction;
import com.theokanning.openai.assistants.run_step.StepDetails;
import com.theokanning.openai.assistants.thread.Attachment;
import com.theokanning.openai.common.LastError;
import com.theokanning.openai.completion.chat.AssistantMessage;
import com.theokanning.openai.completion.chat.AssistantMultipleMessage;
import com.theokanning.openai.completion.chat.ChatFunctionCall;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatToolCall;
import com.theokanning.openai.completion.chat.MultiMediaContent;
import com.theokanning.openai.completion.chat.SystemMessage;
import com.theokanning.openai.completion.chat.ToolMessage;
import com.theokanning.openai.completion.chat.UserMessage;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public static String getSourceId(MessageDb message) {
        if (message == null) {
            return null;
        }

        // 优先检查是否有copy_message_id
        String metadata = message.getMetadata();
        if(metadata != null && !metadata.isBlank()) {
            try {
                Map<String, Object> metadataMap = JacksonUtils.toMap(metadata);
                String copyMessageId = (String) metadataMap.get("copy_message_id");
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
        if(metadata != null && !metadata.isBlank()) {
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
        } else if(content instanceof List<?> contentList) {
            // 列表内容，遍历每个元素
            for (Object item : contentList) {
                if(item instanceof MultiMediaContent mmContent) {

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
            if("text".equals(content.getType())) {
                MultiMediaContent mmContent = new MultiMediaContent();
                mmContent.setType("text");
                mmContent.setText(content.getText().getValue() + attachInfo );
                // 只在一条消息中添加即可
                attachInfo = "";
                result.add(mmContent);
            } else if(content.isVision()){
                MultiMediaContent mmContent = new MultiMediaContent();
                // 目前的chat completion，只支持多模态输入，不支持多模态输出
                if(role.equals("user") && supportVision) {
                    mmContent.setType(content.getType());
                    mmContent.setImageFile(content.getImageFile());
                    mmContent.setImageUrl(content.getImageUrl());
                } else {
                    mmContent.setType("text");
                    StringBuilder sb = new StringBuilder();
                    sb.append("生成了一张图片，");
                    if(content.getImageFile() != null) {
                        sb.append("图片的file_id为：").append(content.getImageFile().getFileId());
                    } else {
                        sb.append("图片的url为：").append(content.getImageUrl().getUrl());
                    }
                    mmContent.setText(sb.toString());
                }
                result.add(mmContent);
            } else if("audio".equals(content.getType())) {
                // 将音频元数据格式化为文本，作为text传递给模型
                AudioData audio = content.getAudioData();
                if (audio != null) {
                    Map<String, Object> audioMap = new HashMap<>();
                    audioMap.put("audio", audio);
                    String audioInfo = Renders.render("templates/audio_transcription.pebble", audioMap);

                    MultiMediaContent mmContent = new MultiMediaContent();
                    mmContent.setType("text");
                    mmContent.setText(audioInfo);
                    result.add(mmContent);
                }
            }
        }

        return result;
    }

    /**
     * 格式化消息，将内容转换为用于chat completion的Message
     */
    public static ChatMessage formatChatCompletionMessage(Message messageInfo, Map<String, FileInfo> fileInfoMap, boolean supportVision) {

        if (messageInfo == null || messageInfo.getRole() == null) {
            return null;
        }
        
        Object content = formatChatCompletionContent(messageInfo.getContent(), messageInfo.getRole(), messageInfo.getAttachments(), fileInfoMap, supportVision);

        switch (messageInfo.getRole()) {
        case "system":
        case "developer":
            return new SystemMessage((String) content);
        case "user":
            UserMessage userMessage = new UserMessage();
            userMessage.setContent(content);
            return userMessage;
        case "assistant":
            if("".equals(content)) {
                if(messageInfo.getIncompleteDetails() == null) {
                    content = "unexpected error";
                } else {
                    content = messageInfo.getIncompleteDetails().getReason();
                }
            }
            // 返回string时一定不存在tool call
            if(content instanceof String s) {
                return new AssistantMessage(s);
            }
            if(content instanceof List<?> contentList) {
                List<ChatToolCall> toolCalls = messageInfo.getContent().stream().filter(messageContent -> messageContent.getType().equals("tool_call"))
                        .map(MessageContent::getToolCall).filter(Objects::nonNull).collect(Collectors.toList());
                if(toolCalls.isEmpty()) {
                    return new AssistantMultipleMessage(contentList.isEmpty() ? "get no answers" : content);
                }
                AssistantMultipleMessage assistantMessage = new AssistantMultipleMessage();
                if(!contentList.isEmpty()) {
                    assistantMessage.setContent(content);
                }
                assistantMessage.setToolCalls(toolCalls);
                return assistantMessage;
            }
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

        if(content instanceof List contentList) {
            List<String> contentTypes = new ArrayList<>();
            for (Object item : contentList) {
                if(item instanceof Map) {
                    Map<String, Object> contentMap = (Map<String, Object>) item;
                    String type = (String) contentMap.get("type");
                    if(type != null) {
                        contentTypes.add(type);
                    }
                } else if(item instanceof MultiMediaContent mmContent) {
                    contentTypes.add(mmContent.getType());
                } else if(item instanceof MessageContent messageContent) {
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
            else if(contentTypes.stream().allMatch(type -> "audio_url".equals(type) || "audio".equals(type))) {
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
            String content = toolResultMessage.getContent();
            if(content == null || content.isBlank()) {
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


    public static ChatToolCall convertToChatToolCall(ToolCall toolCall) {
        ChatToolCall chatToolCall = new ChatToolCall();
        chatToolCall.setId(toolCall.getId());
        chatToolCall.setIndex(toolCall.getIndex());
        chatToolCall.setType("function");
        ChatFunctionCall function = new ChatFunctionCall();
        if(toolCall.getCodeInterpreter() != null) {
            function.setName("code_interpreter");
            function.setArguments(new TextNode(toolCall.getCodeInterpreter().getInput()));
        } else if(toolCall.getFileSearch() != null) {
            if(toolCall.getCodeInterpreter() != null) {
                function.setName("file_search");
                function.setArguments(JacksonUtils.toJsonNode(toolCall.getFileSearch().getRankingOptions()));
            }
        } else {
            function.setName(toolCall.getFunction().getName());
            function.setArguments(toolCall.getFunction().getArguments());
        }
        chatToolCall.setFunction(function);
        return chatToolCall;
    }

    public static ToolMessage convertToToolMessage(ToolCall toolCall, LastError lastError) {
        ToolMessage toolResultMessage = new ToolMessage();
        toolResultMessage.setToolCallId(toolCall.getId());
        if(toolCall.getCodeInterpreter() != null) {
            if(toolCall.getCodeInterpreter().getOutputs() != null) {
                toolResultMessage.setContent(JacksonUtils.serialize(toolCall.getCodeInterpreter().getOutputs()));
            }
        } else if(toolCall.getFileSearch() != null) {
            if(toolCall.getCodeInterpreter() != null) {
                if(toolCall.getFileSearch().getResults() != null) {
                    toolResultMessage.setContent(JacksonUtils.serialize(toolCall.getFileSearch().getResults()));
                }
            }
        } else {
            if(toolCall.getFunction().getOutput() != null) {
                toolResultMessage.setContent(toolCall.getFunction().getOutput());
            }
        }
        String content = toolResultMessage.getContent();
        if(content == null || content.isBlank()) {
            if(lastError != null) {
                toolResultMessage.setContent(JacksonUtils.serialize(lastError));
            } else {
                toolResultMessage.setContent("tool call output is null");
            }
        }
        return toolResultMessage;
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
            if(message instanceof UserMessage userMessage) {
                if(userMessage.getContent() == null) {
                    continue;
                }
                if(userMessage.getContent() instanceof String s) {
                    tokens += TokenCounter.tokenCount(s, EncodingType.O200K_BASE);
                } else if(userMessage.getContent() instanceof Collection) {
                    Collection<?> collections = (Collection<?>) ((UserMessage) message).getContent();
                    for(Object content : collections) {
                        if(content instanceof MultiMediaContent mmContent) {
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

    public static void checkPre(Message pre, Message cur) {
        checkPre(pre, cur, new HashSet<>());
    }

    public static void checkPre(Message pre, Message cur, Set<String> approvalIds) {
        if(pre == null) {
            return;
        }
        if(cur.getRole().equals("system") || cur.getRole().equals("developer")) {
            throw new BizParamCheckException("system or developer message must be the first message");
        }
        if(cur.getRole().equals("user")) {
            if(!pre.getRole().equals("user")) {
                return;
            }
            throw new BizParamCheckException("user message can not follow a user message");
        }
        if(cur.getRole().equals("assistant")) {
            for(MessageContent content : cur.getContent()) {
                if(content.getToolCall() != null) {
                    if(cur.getMetadata().containsKey("approve_request")) {
                        approvalIds.add(cur.getMetadata().get("item_id"));
                    }
                }
            }
            if(pre.getRole().equals("tool") || pre.getRole().equals("user") || pre.getRole().equals("approval")) {
                return;
            }
            throw new BizParamCheckException("assistant message must follow a tool or user message");
        }
        if(cur.getRole().equals("tool")) {
            if(!pre.getRole().equals("assistant")) {
                throw new BizParamCheckException("tool message must follow a assistant message");
            }
            Set<String> toolResultIds = cur.getContent().stream().map(MessageContent::getToolResult).map(ToolMessage::getToolCallId).collect(Collectors.toSet());
            Set<String> toolCallIds = pre.getContent().stream().map(MessageContent::getToolCall)
                    .filter(Objects::nonNull)
                    .map(ChatToolCall::getId).collect(Collectors.toSet());
            toolCallIds.removeAll(approvalIds);
            if(toolCallIds.containsAll(toolResultIds) && toolResultIds.containsAll(toolCallIds)) {
                return;
            }
            throw new BizParamCheckException("1. The tool_call result must be provided for each tool_call_id\n2. All the tool_call_id must be contains in tool_calls.");
        }
        if(cur.getRole().equals("approval")) {
            for(MessageContent content : cur.getContent()) {
                if(content.getApproval() != null) {
                    if(!approvalIds.remove(content.getApproval().getApprovalRequestId())) {
                        throw new BizParamCheckException("invalid approval request id: " + content.getApproval().getApprovalRequestId());
                    }
                }
            }
            return;
        }
        throw new BizParamCheckException("message role must be system or developer or user or assistant or tool");
    }

    public static void checkFirst(MessageRequest messageRequest) {
        if(messageRequest.getRole().equals("user")) {
            return;
        }
        throw new BizParamCheckException("The first message role must be user");
    }

    public static MessageDb convertToolCallMessageFromStepDetails(String threadId, StepDetails details) {
        // Create assistant tool_call message
        MessageDb toolCallMsg = new MessageDb();
        toolCallMsg.setThreadId(threadId);
        toolCallMsg.setRole("assistant");
        Map<String, String> meta = new HashMap<>();
        List<MessageContent> toolCallContent = new ArrayList<>();
        String text = details.getText();
        if(text != null && !text.isBlank()) {
            MessageContent c = new MessageContent();
            c.setType("text");
            c.setText(new Text(text, new ArrayList<>()));
            toolCallContent.add(c);
        }
        for (ToolCall tc : details.getToolCalls()) {
            MessageContent c = new MessageContent();
            c.setType("tool_call");
            c.setToolCall(MessageUtils.convertToChatToolCall(tc));
            toolCallContent.add(c);
        }
        toolCallMsg.setContent(JacksonUtils.serialize(toolCallContent));
        String reasoningContent = details.getReasoningContent();
        if(reasoningContent != null && !reasoningContent.isBlank()) {
            toolCallMsg.setReasoningContent(reasoningContent);
        }
        String reasoningSig = details.getReasoningContentSignature();
        if(reasoningSig != null && !reasoningSig.isBlank()) {
            meta.put(MetaConstants.REASONING_SIG, reasoningSig);
        }
        String redactedReasoning = details.getRedactedReasoningContent();
        if(redactedReasoning != null && !redactedReasoning.isBlank()) {
            meta.put(MetaConstants.REDACTED_REASONING, redactedReasoning);
        }
        toolCallMsg.setMetadata(JacksonUtils.serialize(meta));
        return toolCallMsg;
    }

    public static MessageDb convertToToolResult(String threadId, StepDetails details, LastError lastError) {
        // Create tool_result message (only if outputs exist)
        boolean hasOutput = details.getToolCalls().stream().anyMatch(tc ->
                (tc.getFunction() != null && tc.getFunction().getOutput() != null)
                        || (tc.getCodeInterpreter() != null && tc.getCodeInterpreter().getOutputs() != null)
                        || (tc.getFileSearch() != null && tc.getFileSearch().getResults() != null)
        );
        if(!hasOutput) {
            return null;
        }
        MessageDb toolResultMsg = new MessageDb();
        toolResultMsg.setThreadId(threadId);
        toolResultMsg.setRole("tool");

        List<MessageContent> toolResultContent = new ArrayList<>();
        for (ToolCall tc : details.getToolCalls()) {
            MessageContent rc = new MessageContent();
            rc.setType("tool_result");
            rc.setToolResult(convertToToolMessage(tc, lastError));
            toolResultContent.add(rc);
        }
        toolResultMsg.setContent(JacksonUtils.serialize(toolResultContent));
        return toolResultMsg;
    }

    /**
     * 将MessageDb转换为MessageInfo
     */
    public static Message convertToInfo(MessageDb messageDb) {
        if(messageDb == null) {
            return null;
        }

        Message info = new Message();
        BeanUtils.copyProperties(messageDb, info);

        if(messageDb.getCreatedAt() != null) {
            info.setCreatedAt((int) messageDb.getCreatedAt().toEpochSecond(ZoneOffset.ofHours(8)));
        }

        // 转换metadata从JSON字符串到Map
        String metadata = messageDb.getMetadata();
        if(metadata != null && !metadata.isBlank()) {
            info.setMetadata(JacksonUtils.deserialize(metadata, new TypeReference<>() {}));
        }

        // content字段处理 - 数据库存储的是Content对象数组的JSON
        String content = messageDb.getContent();
        if(content != null && !content.isBlank()) {
            // 数据库中存储的格式：[{"type": "text", "text": {"value": "内容", "annotations": []}}]
            info.setContent(JacksonUtils.deserialize(content, new TypeReference<>() {}));
        }

        // attachments字段反序列化
        String attachments = messageDb.getAttachments();
        if(attachments != null && !attachments.isBlank()) {
            info.setAttachments(JacksonUtils.deserialize(attachments, new TypeReference<>() {}));
        }

        if(info.getMetadata() != null && info.getMetadata().containsKey(MetaConstants.INCOMPLETE_REASON)) {
            IncompleteDetails incompleteDetails = new IncompleteDetails(info.getMetadata().get(MetaConstants.INCOMPLETE_REASON));
            info.setIncompleteDetails(incompleteDetails);
        }

        return info;
    }

}
