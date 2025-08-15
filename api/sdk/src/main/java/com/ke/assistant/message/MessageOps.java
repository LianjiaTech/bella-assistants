package com.ke.assistant.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ke.assistant.common.Attachment;
import com.theokanning.openai.completion.chat.ContentDeserializer;
import com.theokanning.openai.completion.chat.ContentSerializer;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Message 操作相关的 DTO 类
 */
public class MessageOps {

    /**
     * 创建 Message 请求
     */
    @Data
    public static class CreateMessageOp {

        @NotBlank
        private String role;  // "user" | "assistant"

        private String name;

        @JsonDeserialize(using = ContentDeserializer.class)
        @JsonSerialize(using = ContentSerializer.class)
        private Object content;

        @JsonProperty("reasoning_content")
        private String reasoningContent;

        private List<Attachment> attachments;

        private Map<String, Object> metadata;
    }

    /**
     * 更新 Message 请求
     */
    @Data
    public static class UpdateMessageOp {

        private Object content;

        @JsonProperty("reasoning_content")
        private String reasoningContent;

        private Map<String, Object> metadata;

        @JsonProperty("message_status")
        private String messageStatus;
    }
}
