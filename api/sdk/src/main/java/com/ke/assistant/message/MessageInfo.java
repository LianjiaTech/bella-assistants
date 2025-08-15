package com.ke.assistant.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.assistant.common.Attachment;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Message Info DTO for API responses
 */
@Data
public class MessageInfo {
    private String id;
    private String object = "thread.message";
    @JsonProperty("created_at")
    private Integer createdAt;
    @JsonProperty("thread_id")
    private String threadId;
    private String role;
    private Object content;
    @JsonProperty("message_type")
    private String messageType;
    @JsonProperty("reasoning_content")
    private String reasoningContent;
    @JsonProperty("assistant_id")
    private String assistantId;
    @JsonProperty("run_id")
    private String runId;
    private List<Attachment> attachments;
    private Map<String, Object> metadata;
}
