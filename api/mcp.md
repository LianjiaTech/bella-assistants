## MCP 协议详解

**Model Context Protocol (MCP)** 是由 Anthropic 在 2024 年 11 月推出的开源标准协议,用于连接 AI 应用与外部数据源、工具和工作流。它就像 AI 应用的 USB-C 接口,提供了一种标准化的方式让 AI 模型访问各种系统。

### 核心特点

MCP 解决了传统的"N×M 集成问题"——随着 AI 应用数量和数据源的增加,为每个组合创建自定义集成变得难以管理。MCP 提供统一协议,让开发者只需构建一次集成即可。

**三大核心组件:**
- **Tools (工具)** - 可被发现和执行的功能
- **Resources (资源)** - 数据源访问
- **Prompts (提示)** - 预定义的提示模板

## Java 接入方式

Anthropic 与 Spring AI 团队合作推出了官方的 Java SDK,支持同步和异步编程模式。

### 1. 添加 Maven 依赖

```xml
<!-- 核心 MCP SDK -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
</dependency>

<!-- 可选: Spring Boot 集成 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-client-spring-boot-starter</artifactId>
</dependency>

<!-- 可选: WebFlux SSE 传输 -->
<dependency>
    <groupId>org.springframework.experimental</groupId>
    <artifactId>mcp-webflux-sse-transport</artifactId>
</dependency>
```

由于是 milestone 版本,需要添加 Spring 仓库:

```xml
<repositories>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

### 2. 创建 MCP Server (服务端)

SDK 支持多种传输方式:STDIO(标准输入输出)、HTTP SSE(服务器发送事件)和 Streamable-HTTP。

**简单示例:**

```java
// 创建传输提供者
StdioServerTransportProvider provider = 
    new StdioServerTransportProvider(new ObjectMapper());

// 构建同步 MCP Server
McpSyncServer server = McpServer.sync(provider)
    .serverInfo("my-mcp-server", "1.0.0")
    .capabilities(McpSchema.ServerCapabilities.builder()
        .tools(true)
        .build())
    .build();

// 注册工具
server.addTool("weatherTool", weatherToolSpec);

// 启动服务器
server.start();
```

**使用 Spring Boot:**

```java
@Configuration
public class ToolsConfiguration {
    
    @Bean
    public WeatherTool weatherTool(
            WebClient.Builder builder,
            @Value("${WEATHER_API_KEY}") String apiKey) {
        return new WeatherTool(builder, apiKey);
    }
}
```

### 3. 创建 MCP Client (客户端)

MCP Client 负责建立和管理与 MCP 服务器的连接,处理协议版本协商和能力协商。

```java
// 创建客户端传输
ServerParameters params = ServerParameters.builder("npx")
    .args("-y", "@modelcontextprotocol/server-everything")
    .build();
    
McpTransport transport = new StdioClientTransport(params);

// 创建同步客户端
McpSyncClient client = McpClient.sync(transport)
    .clientInfo("my-client", "1.0.0")
    .build();

// 初始化连接
client.initialize();

// 列出可用工具
ListToolsResult tools = client.listTools();

// 调用工具
CallToolResult result = client.callTool(
    new CallToolRequest("toolName", arguments)
);
```

### 4. 架构层次

SDK 采用分层架构:

1. **Client/Server 层** - 处理协议操作
2. **Session 层** - 管理通信模式和状态
3. **Transport 层** - 处理 JSON-RPC 消息序列化

### 5. 传输方式选择

```java
// STDIO 传输(进程间通信)
McpTransport stdioTransport = new StdioClientTransport(params);

// HTTP SSE 传输(远程通信)
McpTransport sseTransport = 
    new HttpClientSseClientTransport("http://mcp-server");

// WebFlux SSE 传输(响应式应用)
McpTransport webfluxTransport = 
    new WebFluxSseClientTransport("http://mcp-server");
```

### 关键优势

- ✅ **标准化集成** - 无需为每个数据源编写自定义连接器
- ✅ **同步/异步支持** - 灵活的编程模型
- ✅ **Spring 集成** - 开箱即用的 Spring Boot Starter
- ✅ **动态工具发现** - 运行时发现和执行工具
- ✅ **类型安全** - 完整的类型安全 API

Java MCP SDK 让企业级应用能够轻松集成 AI 能力,无论是构建智能客服、数据分析工具还是开发辅助系统都非常适用。
