package com.ke.assistant.thread;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Thread 信息 DTO
 */
@Data
public class ThreadInfo {

    private String id;

    private String object = "thread";

    @JsonProperty("created_at")
    private Integer createdAt;

    private String user;

    @JsonProperty("tool_resources")
    private Map<String, Object> toolResources;

    private Map<String, Object> metadata;

    private Map<String, Object> environment;

    // 创建人
    private String owner;
}
