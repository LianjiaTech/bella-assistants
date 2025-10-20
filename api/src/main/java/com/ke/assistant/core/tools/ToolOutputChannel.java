package com.ke.assistant.core.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ke.assistant.core.TaskExecutor;
import com.ke.assistant.core.run.ExecutionContext;
import com.theokanning.openai.assistants.assistant.Tool;

import lombok.extern.slf4j.Slf4j;

/**
 * 控制tool output，只允许一个工具进行输出
 */
@Slf4j
public class ToolOutputChannel implements Runnable {

    private final ExecutionContext context;
    private final ConcurrentHashMap<String, List<Object>> outputCache;
    private final AtomicBoolean endFlag;
    private final CompletableFuture<Void> future;

    public ToolOutputChannel(ExecutionContext context) {
        this.context = context;
        this.outputCache = new ConcurrentHashMap<>();
        this.endFlag = new AtomicBoolean(false);
        this.future = new CompletableFuture<>();
    }

    public static ToolOutputChannel start(ExecutionContext context) {
        ToolOutputChannel channel = new ToolOutputChannel(context);
        TaskExecutor.addToolSender(channel);
        return channel;
    }

    @Override
    public void run() {
        try {
            while (!endFlag.get() || !outputCache.isEmpty()) {
                loop();
            }
        } finally {
            future.complete(null);
        }

    }

    private void loop() {
        try {
            if(outputCache.isEmpty()) {
                Thread.sleep(100);
                return;
            }
            String outputId = context.getCurrentOutputToolCallId();
            if(outputId != null) {
                List<Object> outputs = outputCache.remove(outputId);
                if(outputs == null) {
                    Thread.sleep(100);
                    return;
                }
                for(Object output : outputs) {
                    context.publish(output);
                }
            } else {
                // 当context中没有指定outputID时，随机从map中取一个
                if (!outputCache.isEmpty()) {
                    String randomToolCallId = outputCache.keySet().iterator().next();
                    List<Object> outputs = outputCache.remove(randomToolCallId);
                    if (outputs != null) {
                        context.setCurrentOutputToolCallId(randomToolCallId);
                        for (Object output : outputs) {
                            context.publish(output);
                        }
                    }
                } else {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException e) {
            log.warn(e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    public void output(String toolCallId, Object output) {
        outputCache.computeIfAbsent(toolCallId, key -> new ArrayList<>()).add(output);
    }

    public void output(String toolCallId, Tool tool, Object output) {
        if(tool.hidden()) {
            return;
        }
        outputCache.computeIfAbsent(toolCallId, key -> new ArrayList<>()).add(output);
    }

    public void end() {
        endFlag.set(true);
        try {
            future.get();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
    }
}
