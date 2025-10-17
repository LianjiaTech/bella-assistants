package com.ke.assistant.core.tools;

import com.google.common.collect.Lists;
import com.ke.assistant.core.TaskExecutor;
import com.ke.assistant.core.run.ExecutionContext;
import com.ke.assistant.core.run.RunStateManager;
import com.ke.assistant.core.tools.handlers.definition.CustomToolHandler;
import com.ke.assistant.core.tools.handlers.mcp.McpClientFactory;
import com.ke.assistant.core.tools.handlers.mcp.McpExecuteToolHandler;
import com.ke.assistant.core.tools.handlers.mcp.McpToolListHandler;
import com.ke.assistant.util.MessageUtils;
import com.ke.assistant.util.MetaConstants;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.assistants.run.RequiredAction;
import com.theokanning.openai.assistants.run.SubmitToolOutputs;
import com.theokanning.openai.assistants.run.ToolCall;
import com.theokanning.openai.assistants.run.ToolCallCodeInterpreterOutput;
import com.theokanning.openai.assistants.run.ToolCallFileSearchResult;
import com.theokanning.openai.completion.chat.ChatToolCall;
import com.theokanning.openai.response.tool.definition.MCPTool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 工具执行器
 */
@Slf4j
public class ToolExecutor implements Runnable {

    private final ExecutionContext context;
    private final RunStateManager runStateManager;
    private final Map<String, ToolHandler> toolHandlers;
    private final Map<String, Tool> toolDefinite;

    public ToolExecutor(ExecutionContext context, RunStateManager runStateManager) {
        this.context = context;
        this.runStateManager = runStateManager;
        this.toolHandlers = new HashMap<>();
        this.toolDefinite = new HashMap<>();
    }

    public static ToolExecutor start(ExecutionContext context, RunStateManager runStateManager, ToolFetcher toolFetcher) {
        ToolExecutor toolExecutor = new ToolExecutor(context, runStateManager);
        if(CollectionUtils.isNotEmpty(context.getTools())) {
            context.getTools().stream().filter(tool -> !tool.getType().equals("function")).forEach(tool -> {
                        ToolHandler handler;
                        if(tool instanceof Tool.Custom custom) {
                            handler = new CustomToolHandler(custom.getDefinition());
                        } else if(tool instanceof Tool.MCP mcp) {
                            MCPTool mcpTool = mcp.getDefinition();
                            McpClientFactory.McpClientWrapper mcpClient = McpClientFactory.create(mcpTool.getServerUrl(), mcpTool.getAuthorization(),
                                    mcpTool.getHeaders());
                            handler = new McpToolListHandler(mcpClient, mcpTool, toolExecutor, context);
                        } else {
                            handler = toolFetcher.getToolHandler(tool);
                        }
                        toolExecutor.register(tool, handler);
                    }
            );
        }
        TaskExecutor.addExecutor(toolExecutor);
        return toolExecutor;
    }

    @Override
    public void run() {
        try {
            while (!context.isEnd()) {
                loop();
            }
        } finally {
            toolHandlers.values().forEach(ToolHandler::close);
        }
    }

    private void loop() {
        ToolOutputChannel channel = null;
        try {
            context.toolCallAwait();
            // 当结束时，会唤醒线程，执行结束操作
            if(context.isEnd()) {
                return;
            }
            List<ChatToolCall> tasks = Lists.newArrayList(context.getCurrentToolTasks().values());
            if(tasks.isEmpty()) {
                return;
            }
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            List<ToolCall> requiredTools = new ArrayList<>();
            // 使用 CountDownLatch 确保 requiredTools 构建完成后再处理结果
            CountDownLatch requiredToolsReady = new CountDownLatch(1);

            for (ChatToolCall task : tasks) {
                ToolCall toolCall = MessageUtils.convertToolCall(task);
                if(toolCall.getFunction() == null || toolCall.getFunction().getName() == null) {
                    context.setError("invalid_tool", "tool name is required");
                    break;
                }
                String toolName = toolCall.getFunction().getName();
                ToolHandler handler = toolHandlers.get(toolName);
                if(handler != null) {
                    Tool tool = toolDefinite.get(toolName);
                    if(tool == null) {
                        context.setError("invalid_tool", "tool name: " + toolName + " is not defined");
                        break;
                    }
                    if(handler.isDefinitionHandler()) {
                        requiredTools.add(toolCall);
                    }
                    ToolContext toolContext = buildToolContext(context, tool, task.getId());
                    // 需要输出结果，需要启动channel
                    if((context.isResponseApi() || handler.isFinal()) && channel == null) {
                        channel = ToolOutputChannel.start(context);
                    }
                    ToolOutputChannel finalChannel = channel;
                    CompletableFuture<Void> future = TaskExecutor.supplyCaller(() -> {
                                        Map<String, Object> arguments = JacksonUtils.toMap(task.getFunction().getArguments().asText());
                                        if(arguments == null) {
                                            arguments = new HashMap<>();
                                        }
                                        log.info("tool start: {}, arguments:{}", tool.getType(), JacksonUtils.serialize(arguments));
                                        return handler.execute(toolContext, arguments, finalChannel);
                                    }
                            )
                            .exceptionally(throwable -> {
                                        log.warn(throwable.getMessage(), throwable);
                                        return ToolResult.builder().error(throwable.getMessage()).build();
                                    }
                            )
                            .thenAccept(output -> {
                                // 非服务端执行的工具，不处理结果
                                if(handler.isDefinitionHandler()) {
                                    return;
                                }
                                // 等待 requiredTools 构建完成
                                try {
                                    requiredToolsReady.await(100, TimeUnit.MILLISECONDS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    log.warn("Interrupted while waiting for required tools to be ready", e);
                                }
                                processResult(output, toolCall, context, requiredTools);
                            });
                    futures.add(future);
                } else {
                    requiredTools.add(toolCall);
                }
            }

            // 通知所有异步任务：requiredTools 已构建完成
            requiredToolsReady.countDown();

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );

            allFutures.join();

            //所有内部工具执行完毕，处理Required Action
            if(!requiredTools.isEmpty()) {
                RequiredAction requiredAction = new RequiredAction();
                requiredAction.setType("submit_tool_outputs");
                requiredAction.setSubmitToolOutputs(new SubmitToolOutputs(requiredTools));
                runStateManager.addRequiresAction(context, requiredAction);
            }
        } catch (Exception e) {
            context.setError("tool_execution_error", e.getMessage());
            log.error(e.getMessage(), e);
        } finally {
            // 每次执行完毕，停止channel
            if(channel != null) {
                channel.end();
            }
        }
    }

    private ToolContext buildToolContext(ExecutionContext context, Tool tool, String toolId) {
        ToolContext toolContext = new ToolContext();
        toolContext.setTool(tool);
        toolContext.setFiles(context.getFileIds(tool.getType()));
        toolContext.setToolId(toolId);
        toolContext.setUser(context.getUser());
        toolContext.setBellaContext(context.getBellaContext());
        return toolContext;
    }


    @SuppressWarnings("unchecked")
    private void processResult(ToolResult result, ToolCall toolCall, ExecutionContext context, List<ToolCall> requiredTools) {
        if(result == null || result.isNull()) {
            if(toolCall.getFunction() != null) {
                toolCall.getFunction().setOutput("tool call output is null");
            }
            log.warn("{} output is null", toolCall.getType());
            runStateManager.finishToolCall(context, toolCall, "tool call output is null", requiredTools);
            return;
        }
        log.info("tool end: {}, arguments:{}", toolCall.getType(), JacksonUtils.serialize(result));
        if(result.getError() != null) {
            if(toolCall.getFunction() != null) {
                toolCall.getFunction().setOutput(result.getError());
            }
            log.warn(result.getError());
            runStateManager.finishToolCall(context, toolCall, result.getError(), requiredTools);
            return;
        }
        try {
            if(toolCall.getCodeInterpreter() != null) {
                toolCall.getCodeInterpreter().setOutputs((List<ToolCallCodeInterpreterOutput>) result.getMessage());
            } else if(toolCall.getFileSearch() != null) {
                toolCall.getFileSearch().setResults((List<ToolCallFileSearchResult>) result.getMessage());
            } else if(toolCall.getFunction() != null) {
                toolCall.getFunction().setOutput(JacksonUtils.serialize(result));
            }
            if(result.getAnnotations() != null) {
                context.addAnnotations(result.getAnnotations());
            }
            if(result.getMeta().containsKey(MetaConstants.APPROVAL_ID)) {
                context.addCurrentApprovalId(result.getMeta().get(MetaConstants.APPROVAL_ID));
            }
            runStateManager.finishToolCall(context, toolCall, null, requiredTools);
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            if(toolCall.getFunction() != null) {
                toolCall.getFunction().setOutput(e.getMessage());
            }
            runStateManager.finishToolCall(context, toolCall, e.getMessage(), requiredTools);
        }
    }

    /**
     * 注册工具
     */
    public void register(Tool tool, ToolHandler handler) {
        if(handler != null) {
            String toolName = handler.getToolName();
            toolHandlers.put(toolName, handler);
            toolDefinite.put(toolName, tool);
        }
    }

    public boolean canExecute(String toolName) {
        return toolHandlers.containsKey(toolName);
    }

    public McpToolListHandler getMcpToolListHandler(String serverLabel) {
        ToolHandler handler = toolHandlers.get(serverLabel);
        return handler instanceof McpToolListHandler mcpToolListHandler ? mcpToolListHandler : null;
    }

    public McpExecuteToolHandler getMcpToolHandler(String serverLabel, String name) {
        ToolHandler handler = toolHandlers.get(serverLabel + "_" + name);
        return handler instanceof McpExecuteToolHandler toolHandler ? toolHandler : null;
    }
}
