package com.ke.assistant.core.tools;

import com.google.common.collect.Lists;
import com.ke.assistant.core.TaskExecutor;
import com.ke.assistant.core.run.ExecutionContext;
import com.ke.assistant.core.run.RunStateManager;
import com.ke.assistant.util.MessageUtils;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.assistants.run.RequiredAction;
import com.theokanning.openai.assistants.run.SubmitToolOutputs;
import com.theokanning.openai.assistants.run.ToolCall;
import com.theokanning.openai.assistants.run.ToolCallCodeInterpreterOutput;
import com.theokanning.openai.assistants.run.ToolCallFileSearchResult;
import com.theokanning.openai.completion.chat.ChatToolCall;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 工具执行器
 */
@Slf4j
public class ToolExecutor implements Runnable {

    private final ExecutionContext context;
    private final RunStateManager runStateManager;
    private final Map<String, ToolHandler> toolHandlers;
    private final Map<String, Tool> toolDefinites;

    public ToolExecutor(ExecutionContext context, RunStateManager runStateManager) {
        this.context = context;
        this.runStateManager = runStateManager;
        this.toolHandlers = new HashMap<>();
        this.toolDefinites = new HashMap<>();
    }

    public static void start(ExecutionContext context, RunStateManager runStateManager, ToolFetcher toolFetcher) {
        ToolExecutor toolExecutor = new ToolExecutor(context, runStateManager);
        if(CollectionUtils.isNotEmpty(context.getTools())) {
            context.getTools().forEach( tool -> {
                        ToolHandler handler = toolFetcher.getToolHandler(tool.getType());
                        toolExecutor.register(tool, handler);
                    }
            );
        }
        TaskExecutor.addExecutor(toolExecutor);
    }

    @Override
    public void run() {
        while (!context.isEnd()) {
            loop();
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
            for (ChatToolCall task : tasks) {
                ToolCall toolCall = MessageUtils.convertToolCall(task);
                if(toolCall.getFunction() == null || toolCall.getFunction().getName() == null) {
                    context.setError("invalid_tool", "tool name is required");
                    break;
                }
                String toolName = toolCall.getFunction().getName();
                Tool tool = toolDefinites.get(toolName);
                if(tool == null) {
                    context.setError("invalid_tool", "tool name: " + toolName + "is not defined");
                    break;
                }
                ToolHandler handler = toolHandlers.get(toolName);
                if(handler != null) {
                    // 需要输出结果，需要启动channel
                    if (handler.isFinal() && channel == null) {
                        channel = ToolOutputChannel.start(context);
                    }
                    ToolOutputChannel finalChannel = channel;
                    CompletableFuture<Void> future = TaskExecutor.supplyCaller(() -> handler.execute(tool, task.getFunction().getArguments(), finalChannel))
                            .exceptionally(throwable -> ToolResult.builder().error(throwable.getMessage()).build())
                            .thenAccept(output -> processResult(handler, output, toolCall, context));
                    futures.add(future);
                } else {
                    requiredTools.add(toolCall);
                }
            }

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


    @SuppressWarnings("unchecked")
    private void processResult(ToolHandler handler, ToolResult result, ToolCall toolCall, ExecutionContext context) {
        if(result == null || result.isNull()) {
            if(toolCall.getFunction() != null) {
                toolCall.getFunction().setOutput("tool call output is null");
            }
            log.warn("{} output is null", toolCall.getType());
            runStateManager.finishToolCall(context, toolCall, "tool call output is null");
            return;
        }
        if(result.getError() != null) {
            if(toolCall.getFunction() != null) {
                toolCall.getFunction().setOutput(result.getError());
            }
            log.warn(result.getError());
            runStateManager.finishToolCall(context, toolCall, result.getError());
            return;
        }
        try {
            Object processed = handler.processOutputMessage(result.getOutput());
            if(toolCall.getCodeInterpreter() != null) {
                toolCall.getCodeInterpreter().setOutputs((List<ToolCallCodeInterpreterOutput>) processed);
            } else if(toolCall.getFileSearch() != null) {
                toolCall.getFileSearch().setResults((List<ToolCallFileSearchResult>) processed);
            } else if(toolCall.getFunction() != null) {
                toolCall.getFunction().setOutput((String) processed);
            }
            runStateManager.finishToolCall(context, toolCall, null);
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            if(toolCall.getFunction() != null) {
                toolCall.getFunction().setOutput(e.getMessage());
            }
            runStateManager.finishToolCall(context, toolCall, e.getMessage());
        }
    }

    /**
     * 注册工具
     */
    private void register(Tool tool, ToolHandler handler) {
        String toolName = tool.getType();
        if(handler != null) {
            toolHandlers.put(toolName, handler);
        }
        toolDefinites.put(toolName, tool);
    }
}
