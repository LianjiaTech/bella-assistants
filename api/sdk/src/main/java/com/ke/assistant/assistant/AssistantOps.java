package com.ke.assistant.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.assistant.common.Tool;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Assistant 操作相关的 DTO 类
 */
public class AssistantOps {

    /**
     * 创建 Assistant 请求
     */
    @Data
    public static class CreateAssistantOp {

        @NotBlank
        @Size(max = 256)
        private String model;

        @Size(max = 256)
        private String name;

        @Size(max = 256)
        private String description;

        @Size(max = 32768)
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

        // 权限控制字段
        private String owner;
    }

    /**
     * 更新 Assistant 请求
     */
    @Data
    public static class UpdateAssistantOp {

        @Size(max = 256)
        private String model;

        @Size(max = 256)
        private String name;

        @Size(max = 256)
        private String description;

        @Size(max = 32768)
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

        // 权限控制字段
        private String owner;
    }

    /**
     * 删除 Assistant 请求
     */
    @Data
    public static class DeleteAssistantOp {
        @NotBlank
        private String assistantId;
    }
}
