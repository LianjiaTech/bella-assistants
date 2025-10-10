package com.ke.assistant.core.tools.handlers.mcp;

import com.ke.assistant.core.run.ExecutionContext;
import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolDefinitionHandler;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolStreamEvent;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.response.tool.MCPApprovalRequest;
import com.theokanning.openai.response.tool.MCPListTools;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.Map;

@AllArgsConstructor
public class McpApprovalToolHandler implements ToolDefinitionHandler, McpToolHandler {

    @NotNull
    private final McpToolHandler delegate;

    @NotNull
    private final ExecutionContext executionContext;

    @Override
    public void sendEvent(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        String approvalId = executionContext.getThreadId() + "_" + executionContext.getCurrentToolCallStepId() + "_" + context.getToolId();
        executionContext.addCurrentApprovalId(approvalId);
        MCPApprovalRequest request = new MCPApprovalRequest();
        request.setArguments(JacksonUtils.serialize(arguments));
        request.setServerLabel(delegate.getServerLabel());
        request.setName(delegate.getMcpToolInfo().getName());
        channel.output(context.getToolId(), ToolStreamEvent.builder().toolCallId(context.getToolId())
                .executionStage(ToolStreamEvent.ExecutionStage.prepare)
                .result(request)
                .build());
        channel.output(context.getToolId(), ToolStreamEvent.builder().toolCallId(context.getToolId())
                .executionStage(ToolStreamEvent.ExecutionStage.completed)
                .result(request)
                .build());
    }

    @Override
    public String getToolName() {
        return delegate.getToolName() + "_approval";
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public Map<String, Object> getParameters() {
        return delegate.getParameters();
    }

    @Override
    public boolean isFinal() {
        return delegate.isFinal();
    }

    @Override
    public String getServerLabel() {
        return delegate.getServerLabel();
    }

    @Override
    public MCPListTools.MCPToolInfo getMcpToolInfo() {
        return delegate.getMcpToolInfo();
    }

    @Override
    public Tool.Function getFunction() {
        Tool.Function function = delegate.getFunction();
        function.getFunction().setName(getToolName());
        return function;
    }
}
