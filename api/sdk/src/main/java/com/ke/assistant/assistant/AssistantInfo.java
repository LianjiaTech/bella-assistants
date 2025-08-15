package com.ke.assistant.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.assistant.common.Tool;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Assistant 信息类，用于Service层返回
 */
@Data
public class AssistantInfo {

    private String id;

    private String object = "assistant";

    @JsonProperty("created_at")
    private Integer createdAt;

    private String model;

    private String name;

    private String description;

    private String instructions;

    private List<Tool> tools;

    @JsonProperty("tool_resources")
    private Map<String, Object> toolResources;

    @JsonProperty("file_ids")
    private List<String> fileIds;

    private Map<String, Object> metadata;

    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("response_format")
    private String responseFormat;

    @JsonProperty("reasoning_effort")
    private String reasoningEffort;

    private String user;

}
