package com.ke.assistant.core.tools.handlers.mcp;

import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating MCP (Model Context Protocol) clients with HTTP transport.
 * Supports: - Streamable HTTP transport (recommended for production)
 *
 * @author Bella Assistant Team
 */
@Slf4j
public class McpClientFactory {

    /**
     * Create MCP client with default Streamable HTTP transport
     *
     * @param serverUrl Server URL
     *
     * @return wrapped MCP client
     */
    public static McpClientWrapper create(String serverUrl) {
        return create(serverUrl, null, null);
    }

    /**
     * Create MCP client with authorization
     *
     * @param serverUrl     Server URL
     * @param authorization Authorization header value (e.g., "Bearer token123")
     * @return wrapped MCP client
     */
    public static McpClientWrapper create(String serverUrl, String authorization) {
        return create(serverUrl, authorization, null);
    }

    /**
     * Create MCP client with Streamable HTTP transport and custom headers
     *
     * @param serverUrl Server URL
     * @param headers   HTTP headers
     *
     * @return wrapped MCP client
     */
    public static McpClientWrapper create(String serverUrl, Map<String, String> headers) {
        return create(serverUrl, null, headers);
    }

    /**
     * Create MCP client with Streamable HTTP transport with autorization and custom headers
     *
     * @param serverUrl Server URL
     * @param authorization Authorization header value
     * @param headers   HTTP headers
     *
     * @return wrapped MCP client
     */
    public static McpClientWrapper create(String serverUrl, String authorization, Map<String, String> headers) {
        McpClientConfig config = McpClientConfig.builder()
                .transportType(TransportType.STREAMABLE_HTTP)
                .serverUrl(serverUrl)
                .authorization(authorization)
                .headers(headers)
                .build();
        return create(config);
    }

    /**
     * Create MCP client with custom configuration
     *
     * @param config Client configuration
     *
     * @return wrapped MCP client
     */
    public static McpClientWrapper create(McpClientConfig config) {
        try {
            // Create transport based on type
            McpClientTransport transport = createTransport(config);

            // Build capabilities
            McpSchema.ClientCapabilities.Builder capabilitiesBuilder = McpSchema.ClientCapabilities.builder();
            if(config.isEnableRoots()) {
                capabilitiesBuilder.roots(Boolean.TRUE);
            }
            if(config.isEnableSampling()) {
                capabilitiesBuilder.sampling();
            }

            // Create sync client
            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(config.getRequestTimeout())
                    .capabilities(capabilitiesBuilder.build())
                    .build();

            // Initialize connection
            log.info("Initializing MCP client with transport: {}, serverUrl: {}",
                    config.getTransportType(), config.getServerUrl());
            McpSchema.InitializeResult initResult = client.initialize();
            log.info("MCP client initialized successfully. Server: {}, Version: {}",
                    initResult.serverInfo().name(), initResult.serverInfo().version());

            return new McpClientWrapper(client, config);

        } catch (Exception e) {
            log.error("Failed to create MCP client: {}", e.getMessage(), e);
            throw new McpClientException("Failed to create MCP client", e);
        }
    }

    /**
     * Create Streamable HTTP transport with custom headers support
     */
    private static McpClientTransport createTransport(McpClientConfig config) {
        if(config.getServerUrl() == null || config.getServerUrl().isEmpty()) {
            throw new IllegalArgumentException("Server URL is required for Streamable HTTP transport");
        }

        HttpClientStreamableHttpTransport.Builder builder =
                HttpClientStreamableHttpTransport.builder(config.getServerUrl());

        // Add authorization header if provided
        if(config.getAuthorization() != null && !config.getAuthorization().isEmpty()) {
            builder.customizeRequest(requestBuilder -> requestBuilder.header("Authorization", config.getAuthorization()));
            log.debug("Added Authorization header to MCP transport");
        }

        // Add custom headers if provided
        if(config.getHeaders() != null && !config.getHeaders().isEmpty()) {
            Map<String, String> headers = config.getHeaders();
            builder.customizeRequest(requestBuilder -> headers.forEach(requestBuilder::header));
            log.debug("Added {} custom headers to MCP transport", headers.size() + "");
        }

        return builder.build();
    }

    /**
     * Transport type for MCP client
     */
    public enum TransportType {
        /** Streamable HTTP transport */
        STREAMABLE_HTTP
    }

    /**
     * Configuration for creating MCP clients
     */
    @Data
    @Builder
    public static class McpClientConfig {
        /** Transport type to use */
        @Builder.Default
        private TransportType transportType = TransportType.STREAMABLE_HTTP;

        /** Server URL (for HTTP-based transports) */
        private String serverUrl;

        /** Authorization header value (e.g., "Bearer token123") */
        private String authorization;

        /** HTTP headers (for HTTP-based transports) */
        private Map<String, String> headers;


        /** Request timeout */
        @Builder.Default
        private Duration requestTimeout = Duration.ofSeconds(30);

        /** Enable roots capability */
        @Builder.Default
        private boolean enableRoots = false;

        /** Enable sampling capability */
        @Builder.Default
        private boolean enableSampling = false;
    }

    /**
     * Wrapper class for MCP client with additional utility methods
     */
    @Getter
    public static class McpClientWrapper implements Closeable {
        /**
         * -- GETTER --
         *  Get the underlying sync client
         */
        private final McpSyncClient client;
        /**
         * -- GETTER --
         *  Get client configuration
         */
        private final McpClientConfig config;

        public McpClientWrapper(McpSyncClient client, McpClientConfig config) {
            this.client = client;
            this.config = config;
        }

        /**
         * List all available tools from the MCP server
         *
         * @return list of tools
         */
        public List<McpSchema.Tool> listTools() {
            try {
                McpSchema.ListToolsResult result = client.listTools();
                return result.tools();
            } catch (Exception e) {
                log.error("Failed to list tools: {}", e.getMessage(), e);
                throw new McpClientException("Failed to list tools", e);
            }
        }

        /**
         * Call a tool on the MCP server
         *
         * @param toolName  Tool name
         * @param arguments Tool arguments
         *
         * @return tool execution result
         */
        public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
            try {
                McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, arguments);
                return client.callTool(request);
            } catch (Exception e) {
                log.error("Failed to call tool {}: {}", toolName, e.getMessage(), e);
                throw new McpClientException("Failed to call tool: " + toolName, e);
            }
        }

        /**
         * List all available resources from the MCP server
         *
         * @return list of resources
         */
        public List<McpSchema.Resource> listResources() {
            try {
                McpSchema.ListResourcesResult result = client.listResources();
                return result.resources();
            } catch (Exception e) {
                log.error("Failed to list resources: {}", e.getMessage(), e);
                throw new McpClientException("Failed to list resources", e);
            }
        }

        /**
         * Read resource content
         *
         * @param uri Resource URI
         *
         * @return resource contents
         */
        public List<McpSchema.ResourceContents> readResource(String uri) {
            try {
                McpSchema.ReadResourceRequest request = new McpSchema.ReadResourceRequest(uri);
                McpSchema.ReadResourceResult result = client.readResource(request);
                return result.contents();
            } catch (Exception e) {
                log.error("Failed to read resource {}: {}", uri, e.getMessage(), e);
                throw new McpClientException("Failed to read resource: " + uri, e);
            }
        }

        /**
         * List all available prompts from the MCP server
         *
         * @return list of prompts
         */
        public List<McpSchema.Prompt> listPrompts() {
            try {
                McpSchema.ListPromptsResult result = client.listPrompts();
                return result.prompts();
            } catch (Exception e) {
                log.error("Failed to list prompts: {}", e.getMessage(), e);
                throw new McpClientException("Failed to list prompts", e);
            }
        }

        /**
         * Get server URL
         *
         * @return server URL or null for STDIO transport
         */
        public String getServerUrl() {
            return config.getServerUrl();
        }

        /**
         * Close the MCP client connection
         */
        @Override
        public void close() {
            try {
                log.info("Closing MCP client for server: {}", config.getServerUrl());
                client.close();
            } catch (Exception e) {
                log.error("Error closing MCP client: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Custom exception for MCP client errors
     */
    public static class McpClientException extends RuntimeException {
        public McpClientException(String message) {
            super(message);
        }

        public McpClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

