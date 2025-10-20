package com.ke.assistant.core.tools.handlers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolHandler;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;

/**
 * 网页搜索工具处理器 - 使用Tavily搜索工具处理器
 */
@Component
public class WebSearchToolHandler implements ToolHandler {
    
    @Autowired
    private WebSearchTavilyToolHandler delegator;

    
    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        return delegator.execute(context, arguments, channel);
    }

    @Override
    public String getToolName() {
        return "web_search";
    }
    
    @Override
    public String getDescription() {
        return delegator.getDescription();
    }
    
    @Override
    public Map<String, Object> getParameters() {
        return delegator.getParameters();
    }
    
    @Override
    public boolean isFinal() {
        return delegator.isFinal();
    }
}
