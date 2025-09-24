# Response API接口说明

## Conversation API (/v1/conversations)

### 会话管理接口

**1. 创建会话 (POST /v1/conversations)**
- 作用：创建一个新的会话容器用于管理对话状态
- Request：可包含初始items（最多20个）和metadata元数据
- Response：返回会话对象，包含会话ID、创建时间和元数据

**2. 获取会话 (GET /v1/conversations/{conversation_id})**
- 作用：检索指定ID的会话信息
- Request：会话ID路径参数
- Response：返回完整的会话对象

**3. 更新会话 (POST /v1/conversations/{conversation_id})**
- 作用：更新会话的元数据信息
- Request：新的metadata对象
- Response：返回更新后的会话对象

**4. 删除会话 (DELETE /v1/conversations/{conversation_id})**
- 作用：删除指定会话及其所有内容
- Request：会话ID路径参数
- Response：返回删除确认信息

### 会话项目管理接口

**5. 列出会话项目 (GET /v1/conversations/{conversation_id}/items)**
- 作用：获取会话中的所有项目（消息、工具调用等）
- Request：支持分页参数（after、limit）、排序参数（order）和包含参数（include）
- Response：返回项目列表，包含各种类型的输入消息、输出消息、工具调用等

**6. 创建会话项目 (POST /v1/conversations/{conversation_id}/items)**
- 作用：向会话中添加新的项目
- Request：items数组，包含消息、工具调用输出等，最多20个项目
- Response：返回添加的项目列表

**7. 获取单个会话项目 (GET /v1/conversations/{conversation_id}/items/{item_id})**
- 作用：检索会话中特定项目的详细信息
- Request：会话ID和项目ID路径参数
- Response：返回指定的项目对象

**8. 删除会话项目 (DELETE /v1/conversations/{conversation_id}/items/{item_id})**
- 作用：从会话中删除特定项目
- Request：会话ID和项目ID路径参数
- Response：返回更新后的会话对象

## Response API (/v1/responses)

### 响应生成接口

**1. 创建模型响应 (POST /v1/responses)**
- 作用：OpenAI最先进的响应生成接口，支持文本/图像输入，生成文本/JSON输出
- Request：包含输入内容（input）、模型参数（model、temperature等）、工具配置（tools、tool_choice）、输出格式配置等
- Response：返回完整的响应对象，包含生成的输出、使用统计、工具调用结果等

**2. 获取模型响应 (GET /v1/responses/{response_id})**
- 作用：检索已完成的响应详细信息
- Request：响应ID路径参数，支持流式传输和包含参数
- Response：返回响应对象的完整信息

**3. 删除模型响应 (DELETE /v1/responses/{response_id})**
- 作用：删除指定的响应记录
- Request：响应ID路径参数
- Response：返回删除确认信息

**4. 取消响应 (POST /v1/responses/{response_id}/cancel)**
- 作用：取消正在后台执行的响应生成
- Request：响应ID路径参数（仅限background=true的响应）
- Response：返回响应对象

**5. 列出输入项目 (GET /v1/responses/{response_id}/input_items)**
- 作用：获取用于生成响应的所有输入项目
- Request：响应ID路径参数，支持分页和包含参数
- Response：返回输入项目列表

## Response Stream API

这部分定义了流式响应的事件系统，包含以下主要事件类型：

**响应状态事件：**
- `response.created` - 响应创建时触发
- `response.in_progress` - 响应处理中
- `response.completed` - 响应完成
- `response.failed` - 响应失败
- `response.incomplete` - 响应不完整

**输出内容事件：**
- `response.output_item.added` - 添加输出项目
- `response.content_part.added` - 添加内容部分
- `response.output_text.delta` - 文本增量更新
- `response.output_text.done` - 文本完成

**工具调用事件：**
- `response.function_call_arguments.delta/done` - 函数调用参数
- `response.file_search_call.*` - 文件搜索调用
- `response.web_search_call.*` - 网页搜索调用
- `response.code_interpreter_call.*` - 代码解释器调用
- `response.mcp_call.*` - MCP工具调用

## 核心数据结构

**会话对象**包含：会话ID、创建时间、元数据等基本信息

**响应对象**包含：响应ID、状态、输出内容、使用统计、工具调用结果、推理信息等完整响应数据

**项目对象**支持多种类型：输入消息、输出消息、工具调用、推理内容、图像生成等，每种都有特定的数据结构和状态管理

这些API提供了完整的对话管理和响应生成能力，支持有状态的多轮对话、丰富的工具集成、流式响应和灵活的内容格式。
