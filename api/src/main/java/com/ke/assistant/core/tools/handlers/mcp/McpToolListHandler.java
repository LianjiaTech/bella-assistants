package com.ke.assistant.core.tools.handlers.mcp;

import com.ke.assistant.core.run.ExecutionContext;
import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolExecutor;
import com.ke.assistant.core.tools.ToolHandler;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import com.ke.assistant.core.tools.ToolStreamEvent;
import com.ke.assistant.core.tools.handlers.mcp.McpClientFactory.McpClientWrapper;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.assistants.message.content.Approval;
import com.theokanning.openai.completion.chat.ChatTool;
import com.theokanning.openai.response.stream.McpListToolsCompletedEvent;
import com.theokanning.openai.response.stream.McpListToolsFailedEvent;
import com.theokanning.openai.response.stream.McpListToolsInProgressEvent;
import com.theokanning.openai.response.tool.MCPListTools;
import com.theokanning.openai.response.tool.definition.MCPTool;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Getter
@AllArgsConstructor
public class McpToolListHandler implements ToolHandler {
    @NotNull
    private final McpClientWrapper mcpClient;
    @NotNull
    private final MCPTool mcpToolDefinition;
    @NotNull
    private final ToolExecutor toolExecutor;
    @NotNull
    private final ExecutionContext executionContext;

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        MCPListTools call = new MCPListTools();
        call.setServerLabel(mcpToolDefinition.getServerLabel());
        try {
            if(channel != null) {
                // Prepare event: indicate we are fetching list of tools
                channel.output(context.getToolId(), ToolStreamEvent.builder()
                        .toolCallId(context.getToolId())
                        .executionStage(ToolStreamEvent.ExecutionStage.prepare)
                        .result(call)
                        .event(McpListToolsInProgressEvent.builder().build())
                        .build());
            }

            // Fetch tools from server
            List<MCPListTools.MCPToolInfo> infos = fetchMcpToolInfo();

            call.setTools(infos);

            registerTools(infos);

            if(channel != null) {
                // Completed event
                channel.output(context.getToolId(), ToolStreamEvent.builder()
                        .toolCallId(context.getToolId())
                        .executionStage(ToolStreamEvent.ExecutionStage.completed)
                        .event(McpListToolsCompletedEvent.builder().build())
                        .result(call)
                        .build());
            }
            return new ToolResult(ToolResult.ToolResultType.text, JacksonUtils.serialize(infos));
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            if(channel != null) {
                call.setError(e.getMessage());
                channel.output(context.getToolId(), ToolStreamEvent.builder()
                        .toolCallId(context.getToolId())
                        .executionStage(ToolStreamEvent.ExecutionStage.completed)
                        .event(McpListToolsFailedEvent.builder().build())
                        .result(call)
                        .build());
            }
            return new ToolResult(ToolResult.ToolResultType.text, e.getMessage());
        }
    }

    private List<MCPListTools.MCPToolInfo> toMcpToolInfo(List<McpSchema.Tool> tools) {
        if(tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream().filter(tool -> mcpToolDefinition.allowed(tool.name())).map(tool -> {
            MCPListTools.MCPToolInfo info = new MCPListTools.MCPToolInfo();
            info.setName(tool.name());
            info.setDescription(tool.description());
            info.setAnnotations(JacksonUtils.toMap(tool.annotations()));
            info.setInputSchema(JacksonUtils.toMap(tool.inputSchema()));
            return info;
        }).collect(Collectors.toList());
    }


    public void registerTools(List<MCPListTools.MCPToolInfo> infos) {
        infos.forEach(info -> registerTool(info, null));
    }

    public void registerTool(MCPListTools.MCPToolInfo info, Approval approval) {
        McpToolHandler handler = new McpExecuteToolHandler(mcpToolDefinition.getServerLabel(), mcpClient, info,
                approval == null ? null : approval.getApprovalRequestId(), approval == null || approval.getApprove());
        if(approval == null && mcpToolDefinition.needApproval(info.getName())) {
            handler = new McpApprovalToolHandler(handler, executionContext);
        }
        Tool.Function function = handler.getFunction();
        toolExecutor.register(function, handler);
        ChatTool chatTool = new ChatTool();
        chatTool.setFunction(function.getFunction());
        executionContext.addChatTool(chatTool);
    }

    public List<MCPListTools.MCPToolInfo> fetchMcpToolInfo() {
        // Fetch tools from server
        List<McpSchema.Tool> tools = mcpClient.listTools();

        return toMcpToolInfo(tools);
    }



    @Override
    public String getToolName() {
        return mcpToolDefinition.getServerLabel();
    }

    @Override
    public String getDescription() {
        return mcpToolDefinition.getServerDescription();
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of();
    }

    @Override
    public boolean isFinal() {
        return false;
    }

    @Override
    public void close() {
        mcpClient.close();
    }
}
