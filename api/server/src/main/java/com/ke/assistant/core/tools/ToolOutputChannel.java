package com.ke.assistant.core.tools;

import com.ke.assistant.core.TaskExecutor;
import com.ke.assistant.core.run.ExecutionContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 控制tool output，只允许一个工具进行输出
 */
@Slf4j
public class ToolOutputChannel implements Runnable {

    private final ExecutionContext context;
    private final ConcurrentHashMap<String, List<Object>> outputCache;
    private final AtomicBoolean endFlag;

    public ToolOutputChannel(ExecutionContext context) {
        this.context = context;
        this.outputCache = new ConcurrentHashMap<>();
        this.endFlag = new AtomicBoolean(false);
    }

    public static ToolOutputChannel start(ExecutionContext context) {
        ToolOutputChannel channel = new ToolOutputChannel(context);
        TaskExecutor.addToolSender(channel);
        return channel;
    }

    @Override
    public void run() {
        while (!endFlag.get()) {
            loop();
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
        if(!outputCache.containsKey(toolCallId)) {
            outputCache.put(toolCallId, new ArrayList<>());
        }
        outputCache.get(toolCallId).add(output);
    }

    public void end() {
        endFlag.set(true);
    }

}
