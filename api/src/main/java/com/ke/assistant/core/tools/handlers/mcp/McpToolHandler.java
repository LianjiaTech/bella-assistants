package com.ke.assistant.core.tools.handlers.mcp;

import com.ke.assistant.core.tools.ToolHandler;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.response.tool.MCPListTools;

public interface McpToolHandler extends ToolHandler {
    String getServerLabel();
    MCPListTools.MCPToolInfo getMcpToolInfo();
    Tool.Function getFunction();
}
