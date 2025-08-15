package com.ke.assistant.run;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.assistant.common.Tool;
import com.ke.bella.openapi.BaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Run 信息 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RunInfo extends BaseDto {

    @NotBlank
    private String id;

    private String object = "thread.run";

    @JsonProperty("created_at")
    private Long createdAt;

    @NotBlank
    @JsonProperty("thread_id")
    private String threadId;

    @NotBlank
    @JsonProperty("assistant_id")
    private String assistantId;

    @NotBlank
    private String status;

    @JsonProperty("required_action")
    private RequiredAction requiredAction;

    @JsonProperty("last_error")
    private LastError lastError;

    @JsonProperty("expires_at")
    private Long expiresAt;

    @JsonProperty("started_at")
    private Long startedAt;

    @JsonProperty("cancelled_at")
    private Long cancelledAt;

    @JsonProperty("failed_at")
    private Long failedAt;

    @JsonProperty("completed_at")
    private Long completedAt;

    @JsonProperty("incomplete_details")
    private Object incompleteDetails;

    private String model;

    private String instructions;

    private List<Tool> tools;

    private Map<String, Object> metadata;

    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("max_prompt_tokens")
    private Integer maxPromptTokens;

    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;

    @JsonProperty("truncation_strategy")
    private Object truncationStrategy;

    @JsonProperty("tool_choice")
    private Object toolChoice;

    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;

    @JsonProperty("response_format")
    private Object responseFormat;

    private Usage usage;

    @JsonProperty("reasoning_effort")
    private String reasoningEffort;

    @JsonProperty("reasoning_time")
    private Long reasoningTime;

    private String user;

    @Data
    public static class RequiredAction {
        private String type;
        @JsonProperty("submit_tool_outputs")
        private SubmitToolOutputs submitToolOutputs;

        @Data
        public static class SubmitToolOutputs {
            @JsonProperty("tool_calls")
            private List<ToolCall> toolCalls;
        }

        @Data
        public static class ToolCall {
            private String id;
            private String type;
            private Function function;

            @Data
            public static class Function {
                private String name;
                private String arguments;
            }
        }
    }

    @Data
    public static class LastError {
        private String code;
        private String message;
    }

    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
