package com.ke.assistant.mcp;

import com.ke.assistant.core.tools.handlers.mcp.McpClientFactory;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.groovy.util.Maps;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
public class McpTest {

    @Test
    public void testMcp() {
        McpClientFactory.McpClientWrapper client = McpClientFactory.create("https://mcp.context7.com/mcp");
        List<McpSchema.Tool> tools = client.listTools();
        log.info("available tools: {}", tools.size());
        Assertions.assertEquals(2, tools.size(), "There should be two available tools");
        for (McpSchema.Tool tool : tools) {
            log.info("tool: {}", tool.name());
            log.info("description: {}", tool.description());
            log.info("input:{}", tool.inputSchema());
        }
        McpSchema.CallToolResult result = client.callTool("resolve-library-id", Maps.of("libraryName", "bella-openapi"));
        Assertions.assertNotNull(result.content(), "There should be a result");
        log.info("content: {}", result.content());
    }
}
