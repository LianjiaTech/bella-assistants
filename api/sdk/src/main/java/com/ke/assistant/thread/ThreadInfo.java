package com.ke.assistant.thread;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ke.assistant.common.LocalDateTimeToSecondsSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Thread 信息 DTO
 */
@Data
public class ThreadInfo {

    private String id;

    private String object = "thread";

    @JsonProperty("created_at")
    @JsonSerialize(using = LocalDateTimeToSecondsSerializer.class)
    private LocalDateTime createdAt;

    private String user;

    @JsonProperty("tool_resources")
    private Map<String, Object> toolResources;

    private Map<String, Object> metadata;

    private Map<String, Object> environment;

    // 创建人
    private String owner;
}
