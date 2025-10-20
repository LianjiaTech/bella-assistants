package com.ke.assistant.core.tools;

import java.util.Map;

import com.ke.bella.openapi.BellaContext;

/**
 * 使用Bella Endpoint实现的工具，执行时需要传递BellaContext上下文
 */
public abstract class BellaToolHandler implements ToolHandler {

    public final ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        try{
            BellaContext.replace(context.getBellaContext());
            return doExecute(context, arguments, channel);
        } finally {
            BellaContext.clearAll();
        }
    }

    protected abstract ToolResult doExecute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel);

}
