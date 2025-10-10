package com.ke.assistant.core.tools.handlers.mcp;

import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolHandler;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import com.ke.assistant.core.tools.ToolStreamEvent;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.response.stream.McpCallArgumentsDeltaEvent;
import com.theokanning.openai.response.stream.McpCallArgumentsDoneEvent;
import com.theokanning.openai.response.stream.McpCallCompletedEvent;
import com.theokanning.openai.response.stream.McpCallFailedEvent;
import com.theokanning.openai.response.stream.McpCallInProgressEvent;
import com.theokanning.openai.response.tool.MCPListTools;
import com.theokanning.openai.response.tool.MCPToolCall;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotNull;
import java.util.Map;

@Slf4j
@AllArgsConstructor
public class McpToolHandler implements ToolHandler {

    @NotNull
    private final String serverLale;

    @NotNull
    private final McpClientFactory.McpClientWrapper mcpClient;

    @NotNull
    private final MCPListTools.MCPToolInfo mcpToolInfo;

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        String ouput;
        MCPToolCall call = new MCPToolCall();
        call.setName(mcpToolInfo.getName());
        call.setArguments("");
        call.setServerLabel(serverLale);
        String argumentStr = JacksonUtils.serialize(arguments);
        if(channel != null) {
            channel.output(context.getToolId(), ToolStreamEvent.builder()
                    .toolCallId(context.getToolId())
                    .executionStage(ToolStreamEvent.ExecutionStage.prepare)
                    .result(call)
                    .event(McpCallInProgressEvent.builder().build())
                    .build());
        }
        try {
            if(channel != null) {
                channel.output(context.getToolId(), ToolStreamEvent.builder()
                        .toolCallId(context.getToolId())
                        .executionStage(ToolStreamEvent.ExecutionStage.processing)
                        .result(call)
                        .event(McpCallArgumentsDeltaEvent.builder()
                                .delta(argumentStr)
                                .build())
                        .build());
                channel.output(context.getToolId(), ToolStreamEvent.builder()
                        .toolCallId(context.getToolId())
                        .executionStage(ToolStreamEvent.ExecutionStage.processing)
                        .result(call)
                        .event(McpCallArgumentsDoneEvent.builder()
                                .arguments(argumentStr)
                                .build())
                        .build());
            }
            McpSchema.CallToolResult result = mcpClient.callTool(mcpToolInfo.getName(), arguments);
            call.setArguments(argumentStr);
            if(Boolean.TRUE == result.isError()) {
                ouput = "mcp tool execution failed";
            } else {
                if(result.structuredContent() != null) {
                    ouput = JacksonUtils.serialize(result.structuredContent());
                } else {
                    ouput = JacksonUtils.serialize(result.content());
                }
            }
            call.setOutput(ouput);
            if(channel != null) {
                channel.output(context.getToolId(), ToolStreamEvent.builder()
                        .toolCallId(context.getToolId())
                        .executionStage(ToolStreamEvent.ExecutionStage.completed)
                        .result(call)
                        .event(Boolean.TRUE == result.isError() ? McpCallFailedEvent.builder().build() : McpCallCompletedEvent.builder().build())
                        .build()
                );
            }
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            if(channel != null) {
                channel.output(context.getToolId(), ToolStreamEvent.builder()
                        .toolCallId(context.getToolId())
                        .executionStage(ToolStreamEvent.ExecutionStage.completed)
                        .result(call)
                        .event(McpCallFailedEvent.builder().build())
                        .build()
                );
            }
            ouput = e.getMessage();
        }
        return new ToolResult(ToolResult.ToolResultType.text, ouput);
    }

    @Override
    public String getToolName() {
        return serverLale + "_" + mcpToolInfo.getName();
    }

    @Override
    public String getDescription() {
        return mcpToolInfo.getDescription();
    }

    @Override
    public Map<String, Object> getParameters() {
        return mcpToolInfo.getInputSchema();
    }

    @Override
    public boolean isFinal() {
        return false;
    }

    public Tool.Function getFunction() {
        Tool.Function function = new Tool.Function();
        Tool.FunctionDefinition functionDefinition = new Tool.FunctionDefinition();
        functionDefinition.setName(getToolName());
        functionDefinition.setDescription(getDescription());
        functionDefinition.setParameters(getParameters());
        function.setFunction(functionDefinition);
        function.setIsFinal(isFinal());
        return function;
    }
}
