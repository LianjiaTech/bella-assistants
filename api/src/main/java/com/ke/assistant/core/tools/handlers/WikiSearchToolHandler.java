package com.ke.assistant.core.tools.handlers;

import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolHandler;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 维基百科搜索工具处理器 - 使用Tavily搜索工具处理器
 */
@Component
public class WikiSearchToolHandler implements ToolHandler {
    @Autowired
    private WebSearchTavilyToolHandler delegator;


    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        return delegator.execute(context, arguments, channel);
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

    
    @Override
    public String getToolName() {
        return "wiki_search";
    }

}
