package com.ke.assistant.core.tools;

import java.util.Map;

/**
 * 服务端并非实际执行工具，只是处理工具参数
 */
public interface ToolDefinitionHandler extends ToolHandler {

    @Override
    default ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        sendEvent(context, arguments, channel);
        return ToolResult.builder().build();
    }

    void sendEvent(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel);

    @Override
    default boolean isDefinitionHandler(){
        return true;
    }
}
