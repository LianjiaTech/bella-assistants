package com.ke.assistant.core.tools;

import com.theokanning.openai.response.stream.BaseStreamEvent;
import com.theokanning.openai.response.tool.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolStreamEvent {
    private String toolCallId;
    private BaseStreamEvent event;
    private ExecutionStage executionStage;
    private ToolCall result;

    public enum ExecutionStage {
        prepare,
        processing,
        output,
        completed
    }
}
