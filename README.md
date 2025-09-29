# Bella Assistant API

> 基于 Spring Boot 的智能助手 API 服务，提供兼容OpenAI Assistants API和Responses API的开源实现，突破原生生态限制，支持灵活切换各大厂商模型，真正实现"一次开发，处处可用"。内置多种工具集成和文件处理功能。

## 🚀 项目概述

Bella Assistant 是一个企业级的智能助手 API 服务，实现了完整的对话管理、工具调用和流式响应功能。该系统采用先进的执行引擎架构，支持多工具并行执行、智能规划决策和内存管理。

### 🎯 核心特性

- **OpenAI 兼容 API**: 完整覆盖 Assistants API（Assistants/Threads/Messages/Runs）
- **Responses API 支持**: 兼容 OpenAI Responses API（创建支持 SSE 流式）
- **多工具集成**: 内置天气、网页搜索、爬虫、图表、RAG/检索、图像生成、语音转写、视觉识别等
- **流式响应**: 支持 Server-Sent Events (SSE) 实时流式输出（Run 与 Response 创建）
- **智能规划**: 基于模板的规划系统，自动决策执行流程
- **文件处理**: S3/MinIO 文件上传存储与引用，支持公共访问 URL
- **内存管理**: 自动管理对话上下文长度，支持长对话
- **并行执行**: 多工具并行调用，提升响应效率

## 🛠 技术栈

- **框架**: Spring Boot 2.7.18
- **数据库**: MySQL 8.0 + JOOQ
- **缓存**: Redis (Redisson)
- **存储**: AWS S3 / MinIO
- **模板引擎**: Pebble Templates
- **文档**: Swagger/OpenAPI 3
- **监控**: Spring Boot Actuator
- **配置中心**: Apollo (可选)

## 🔧 快速开始

### 环境要求

- Java 1.8+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

### 1. 克隆项目

```bash
git clone https://github.com/yourusername/bella-assistants.git
cd bella-assistants/api
```

### 2. 数据库初始化

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE bella_assistant CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 执行初始化脚本
mysql -u root -p bella_assistant < sql/01-init-tables.sql
```

### 3. 配置文件

修改 `src/main/resources/application.yml` 中的数据库和 Redis 连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/bella_assistant?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
  redis:
    host: localhost
    port: 6379
bella:
  openapi:
    host: http://localhost:8080
  assistant:
    s3:
      bucket-name: bella-assistant
      endpoint: http://localhost:9000   # MinIO 示例
      access-key: minio
      secret-key: minio123
      path-style-access: true
```

### 4. 构建和运行

```bash
# 生成 JOOQ 代码
mvn org.jooq:jooq-codegen-maven:generate

# 编译项目
mvn clean compile

# 运行应用
mvn spring-boot:run
```

应用启动后访问:
- API 文档: http://localhost:8087/docs/index.html
- 健康检查: http://localhost:8087/actuator/health

## 📖 API 使用

### Responses API（创建与查询）

创建响应（支持 SSE 流式）：

```bash
curl -X POST http://localhost:8087/v1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "input": [{"role":"user","content":[{"type":"input_text","text":"你好"}]}],
    "stream": true
  }'
```

查询响应执行结果：

```bash
curl -X GET http://localhost:8087/v1/responses/{response_id}
```

说明：`GET /v1/responses/{response_id}` 暂不支持流式返回，创建时 `stream=true` 可获得 SSE 流。

### 创建助手

```bash
curl -X POST http://localhost:8087/v1/assistants \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "name": "我的助手",
    "instructions": "你是一个有用的助手",
    "tools": [{"type": "web_search"}]
  }'
```

### 创建对话线程

```bash
curl -X POST http://localhost:8087/v1/threads \
  -H "Content-Type: application/json" \
  -d '{}'
```

### 发送消息并运行

```bash
curl -X POST http://localhost:8087/v1/threads/{thread_id}/runs \
  -H "Content-Type: application/json" \
  -d '{
    "assistant_id": "{assistant_id}",
    "additional_messages": [{
      "role": "user",
      "content": "今天天气怎么样?"
    }],
    "stream": true
  }'
```

## 🏗 项目架构

### 核心模块

```
src/main/java/com/ke/assistant/
├── controller/          # REST API 控制器
├── service/            # 业务逻辑服务层
├── core/               # 核心执行引擎
│   ├── run/            # 运行执行器
│   ├── plan/           # 智能规划系统
│   ├── tools/          # 工具系统
│   ├── ai/             # AI 模型调用
│   ├── memory/         # 内存管理
│   └── file/           # 文件处理
├── db/                 # 数据访问层
└── configuration/      # 配置类
```

### 执行流程

1. **创建运行**: 客户端创建线程和运行请求
2. **上下文构建**: 组装执行上下文（消息、工具、文件）
3. **智能规划**: 规划器决定下一步动作（LLM 调用、工具执行、完成）
4. **并行执行**: 运行执行器协调 AI 服务和工具执行
5. **流式输出**: 实时 SSE 流式响应给客户端
6. **状态管理**: 管理运行状态转换和错误处理

注：Response 的创建同样支持 SSE 流式输出；Response 的 GET 查询目前为非流式。

## 🔨 开发指南

### 添加新工具

1. 实现 `ToolHandler` 接口：

```java
@Component
public class MyToolHandler implements ToolHandler {
    @Override
    public String getType() {
        return "my_tool";
    }
    
    @Override
    public ToolResult handle(ToolContext context) {
        // 工具逻辑实现
        return ToolResult.success("结果");
    }
}
```

2. 在 `application.yml` 中添加配置：

```yaml
bella:
  assistant:
    tools:
      my_tool:
        enabled: true
        config:
          api_url: "https://api.example.com"
```

3. 在 `ToolFetcher` 中注册工具处理器

### 数据库变更

```bash
# 1. 修改 MySQL 数据库结构
# 2. 重新生成 JOOQ 代码
mvn org.jooq:jooq-codegen-maven:generate
# 3. 更新相应的 Repository 方法
```

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=AssistantControllerTest
```

## 🔧 运维部署

### Docker 部署

```bash
# 构建镜像
docker build -t bella-assistant .

# 运行容器
docker run -d \
  --name bella-assistant \
  -p 8087:8087 \
  -e SPRING_PROFILES_ACTIVE=prod \
  bella-assistant
```

### 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `server.port` | 服务端口 | 8087 |
| `bella.assistant.tools.*.enabled` | 工具开关 | true |
| `bella.s3.endpoint` | S3 存储端点 | - |
| `bella.assistant.max-context-length` | 最大上下文长度 | 32000 |

环境变量与常用配置（摘自 `api/src/main/resources/application.yml`）：

- `S3_BUCKET_NAME`：S3/MinIO 存储桶名称（默认 `bella-assistant`）
- `S3_REGION`、`S3_ACCESS_KEY`、`S3_SECRET_KEY`、`S3_ENDPOINT`、`S3_PATH_STYLE_ACCESS`、`S3_PUBLIC_BASE_URL`
- `WEB_SEARCH_TAVILY_APIKEY`：Tavily 搜索 API Key
- `WEATHER_SEARCH_APIKEY`：高德天气 API Key
- `RETRIEVAL_URL`：检索服务地址；`RAG_TOOL_URL`：RAG 服务地址

### 监控指标

应用提供以下监控端点：

- `/actuator/health` - 健康检查
- `/actuator/metrics` - 应用指标
- `/actuator/prometheus` - Prometheus 格式指标

## 📝 内置工具

| 工具类型 | 功能描述 | 配置键 |
|----------|----------|--------|
| `web_search_tavily` | 网页搜索（Tavily） | `bella.assistant.tools.web-search-tavily.*` |
| `web_crawler` | 站点爬取 | `bella.assistant.tools.web-crawler.*` |
| `weather_search` | 天气查询（高德） | `bella.assistant.tools.weather-search.*` |
| `rag` | RAG 检索 | `bella.assistant.tools.rag.*` |
| `retrieval` | 语义检索 | `bella.assistant.tools.retrieval.*` |
| `image_generate` | 图像生成（DALL·E 3） | `bella.assistant.tools.image-generate.*` |
| `img_vision` | 图像理解（Vision） | `bella.assistant.tools.img-vision.*` |
| `bar_tool` | 柱状图生成 | `bella.assistant.tools.bar-tool.*` |
| `line_tool` | 折线图生成 | `bella.assistant.tools.line-tool.*` |
| `pie_tool` | 饼图生成 | `bella.assistant.tools.pie-tool.*` |
| `audio_transcription` | 语音转写 | `bella.assistant.tools.audio-transcription.*` |
| `read_files` | 文件读取 | `bella.assistant.tools.read-files.*` |

说明：`Local Shell` 为工具定义事件（服务器不会执行本地命令），用于兼容 Responses API 的工具调用流。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

### 开发流程

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m '添加某个特性'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件。

## 🆘 支持

如果遇到问题，请：

1. 查看 [文档](https://doc.bella.top/docs/bella-assistant/intro)
2. 搜索已有的 [Issues](issues)
3. 创建新的 Issue 描述问题

---

**Built with ❤️ by Bella Team**
