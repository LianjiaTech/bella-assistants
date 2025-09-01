package com.ke.assistant.core.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.openapi.BellaContext;

/**
 * 使用Bella Endpoint实现的工具，执行时需要传递BellaContext上下文
 */
public interface BellaToolHandler extends ToolHandler {

    default ToolResult execute(ToolContext context, JsonNode arguments, ToolOutputChannel channel) {
        try{
            BellaContext.replace(context.getBellaContext());
            return doExecute(context, arguments, channel);
        } finally {
            BellaContext.clearAll();
        }
    }

   ToolResult doExecute(ToolContext context, JsonNode arguments, ToolOutputChannel channel);

}
