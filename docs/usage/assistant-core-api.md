# Assistant API 核心接口

Assistant API 遵循 OpenAI Assistant API 规范，提供完整的智能体创建和执行能力。
> 每个接口的详细参数，参考：https://platform.openai.com/docs/api-reference/assistants

## 核心执行接口

### 1. **createThreadAndRun** - 一站式执行接口
**端点**: `POST /v1/threads/runs`

这是最便捷的执行方式，一次调用即可完成智能体对话。

**主要参数**：
- `assistant_id`: 智能体ID（必需）
- `thread`: 可选，创建新会话的配置
- `additional_messages`: 用户消息列表
- `tools`: 运行时覆盖智能体工具配置
- `stream`: 是否流式返回（默认false）
- `model`: 覆盖智能体模型
- `instructions`: 覆盖智能体系统提示

**特点**：
- 自动创建Thread和Run
- 适合无状态的单次对话
- 支持SSE流式响应

### 2. **createRun** - 标准执行接口
**端点**: `POST /v1/threads/{thread_id}/runs`

在已有会话中执行智能体，适合多轮对话。

**依赖接口**：
- 需要先创建Assistant：`POST /v1/assistants`
- 需要先创建Thread：`POST /v1/threads`
- 可选创建Message：`POST /v1/threads/{thread_id}/messages`

**执行流程**：
```
1. 创建Assistant（定义智能体能力）
2. 创建Thread（创建会话）
3. 添加用户Message（可选，也可通过additional_messages）
4. 创建Run（执行智能体）
5. 处理工具调用（如需要）
```

**工具调用处理**：
当Run状态变为`requires_action`时，需调用：
- `POST /v1/threads/{thread_id}/runs/{run_id}/submit_tool_outputs`

## 依赖接口详解

### **Assistant管理**
- **创建**: `POST /v1/assistants`
  - 定义智能体名称、模型、系统提示、工具配置
  - 支持内置工具：weather、web_search、image_generation、chart、rag等
- **查询**: `GET /v1/assistants/{assistant_id}`
- **更新**: `POST /v1/assistants/{assistant_id}`

### **Thread管理**
- **创建**: `POST /v1/threads`
  - 可携带初始消息
  - 支持文件附件
- **Fork**: `POST /v1/threads/{thread_id}/fork` - 复制会话历史
- **合并**: `POST /v1/threads/{from_id}/merge_to/{to_id}` - 合并会话

### **Message管理**
- **创建**: `POST /v1/threads/{thread_id}/messages`
- **列表**: `GET /v1/threads/{thread_id}/messages`
- **更新/删除**: 支持消息编辑和删除