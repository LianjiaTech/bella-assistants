# Response API 完整使用文档

## 概述

Response API 是一个轻量、灵活的对话接口，支持多模态输入、工具调用、推理模式等高级特性。
> 完整的请求协议见：https://platform.openai.com/docs/api-reference/responses/create
> 完整的响应协议见：https://platform.openai.com/docs/api-reference/responses/object
> 全部流式事件见：https://platform.openai.com/docs/api-reference/responses-streaming

## 核心端点

### 创建响应
**端点**: `POST /v1/responses`  
**认证**: Bearer Token

### 获取响应详情
**端点**: `GET /v1/responses/{response_id}`  
**认证**: Bearer Token

## 核心功能详解

### 1. 基础对话

#### 简单文本对话
```json
{
  "model": "gpt-5-nano",
  "input": "How much gold would it take to coat the Statue of Liberty in a 1mm layer?",
  "stream": true
}
```

#### 带推理模式的对话
```json
{
  "model": "gpt-5-nano",
  "input": [{
    "role": "user",
    "content": "复杂问题..."
  }],
  "store": true,
  "reasoning": {
    "effort": "medium",
    "summary": "auto" 
  },
  "stream": true
}
```

### 2. 会话管理

#### 续接上次对话
```json
{
  "model": "gpt-5-nano",
  "previous_response_id": "resp_1000012001",
  "input": "继续上面的话题...",
  "store": true,
  "stream": true
}
```

#### 基于历史会话
```json
{
  "model": "gpt-5-nano",
  "conversation": "conv_xxx",
  "input": "基于之前的讨论...",
  "store": true
}
```

### 3. 多模态输入

Response API支持多种输入模态的组合使用：
- **文本**（input_text）
- **图片**（input_image）
- **音频**（input_audio）
- **文件**（input_file）

#### 文件输入
```json
{
  "model": "gpt-5-nano",
  "input": [{
    "role": "user",
    "content": [
      {
        "type": "input_file",
        "file_id": "file-2509221659180022489862-1989906366"
      },
      {
        "type": "input_text",
        "text": "他的学历是什么？"
      }
    ]
  }],
  "store": false,
  "stream": true
}
```

#### 图片输入（Base64）
```json
{
  "model": "gpt-5-nano",
  "input": [{
    "role": "user",
    "content": [
      {
        "type": "input_image",
        "image_url": "data:image;base64,/9j/4AAQSkZJRgAB..."
      },
      {
        "type": "input_text",
        "text": "描述这张图片"
      }
    ]
  }],
  "stream": true
}
```

#### 音频输入
```json
{
  "model": "gpt-5-nano",
  "input": [{
    "role": "user",
    "content": [
      {
        "type": "input_audio",
        "input_audio": {
          "format": "wav",
          "data": "UklGRv////9XQVZFZm10..."
        }
      },
      {
        "type": "input_text",
        "text": "这段语音是什么内容？"
      }
    ]
  }],
  "reasoning": {
    "effort": "medium",
    "summary": "auto"
  },
  "stream": true
}
```

### 4. 工具调用

#### 函数工具（Function Tools）

##### 基础函数定义
```json
{
  "model": "gpt-5",
  "input": [{
    "type": "message",
    "role": "user",
    "content": "北京和上海现在的天气怎么样？"
  }],
  "tools": [{
    "type": "function",
    "name": "get_weather",
    "description": "Retrieves current weather for the given location.",
    "parameters": {
      "type": "object",
      "properties": {
        "location": {
          "type": "object",
          "properties": {
            "x": {"type": "string"},
            "y": {"type": "string"}
          },
          "required": ["x", "y"]
        },
        "units": {
          "type": "string",
          "enum": ["celsius", "fahrenheit"]
        }
      },
      "required": ["location", "units"]
    },
    "strict": true
  }]
}
```

##### 包含函数调用历史的对话
```json
{
  "model": "gpt-5-nano",
  "input": [
    {
      "type": "message",
      "role": "user",
      "content": "北京现在的天气怎么样？"
    },
    {
      "type": "function_call",
      "status": "completed",
      "call_id": "call_weather456",
      "name": "get_weather",
      "arguments": "{\"city\": \"北京\", \"unit\": \"celsius\"}"
    },
    {
      "status": "completed",
      "type": "function_call_output",
      "call_id": "call_weather456",
      "output": "{\"temperature\": 15, \"condition\": \"晴朗\"}"
    },
    {
      "type": "message",
      "role": "user",
      "content": "比较一下上海的天气"
    }
  ],
  "tools": [{
    "type": "function",
    "name": "get_weather",
    "description": "Retrieves current weather for the given location.",
    "parameters": {
      "type": "object",
      "properties": {
        "location": {
          "type": "object",
          "properties": {
            "x": {"type": "string"},
            "y": {"type": "string"}
          },
          "required": ["x", "y"]
        },
        "units": {
          "type": "string",
          "enum": ["celsius", "fahrenheit"]
        }
      },
      "required": ["location", "units"]
    },
    "strict": true
  }]
}
```

#### 网络搜索工具（Web Search）
```json
{
  "model": "gpt-5-nano",
  "input": [{
    "type": "message",
    "role": "user",
    "content": "使用网络搜索分别查询今年的房价和贝壳的股价"
  }],
  "tools": [{
    "type": "web_search_preview"
  }],
  "reasoning": {
    "effort": "medium"
  },
  "store": true,
  "stream": true
}
```

#### 图像生成工具
```json
{
  "model": "gpt-5-nano",
  "input": [{
    "type": "message",
    "role": "user",
    "content": "生成一张夏日午后森林的图片和一张冬天雪后森林的图片"
  }],
  "tools": [{
    "type": "image_generation",
    "model": "doubao-seedream-3-0-t2i"
  }],
  "stream": true
}
```

#### 文件搜索工具
```json
{
  "model": "gpt-5-nano",
  "input": [{
    "type": "message",
    "role": "user",
    "content": "他的技能有哪些？"
  }],
  "tools": [{
    "type": "file_search",
    "vector_store_ids": ["file-2509221659180022489862-1989906366"]
  }],
  "stream": true
}
```

#### 本地Shell工具
```json
{
  "model": "gpt-5-nano",
  "input": "使用local_shell工具，列出当前目录下所有以'test_'开头的txt文件",
  "tools": [{
    "type": "local_shell"
  }],
  "store": false,
  "stream": true
}
```

### 5. 自定义工具（Custom Tools）

#### 文本格式工具
```json
{
  "model": "gpt-5-nano",
  "input": [{
    "type": "message",
    "role": "user",
    "content": "人工智能技术在过去十年中取得了显著进展..."
  }],
  "instructions": "使用text_summarizer对用户信息进行摘要",
  "tools": [{
    "name": "text_summarizer",
    "type": "custom",
    "description": "对输入的长文本进行智能摘要",
    "format": {
      "type": "text"
    }
  }]
}
```

#### 正则表达式格式工具
```json
{
  "model": "gpt-5-nano",
  "input": [{
    "type": "message",
    "role": "user",
    "content": "张三，电话：13800138000，国家：中国，邮箱：zhangsan@ke.com"
  }],
  "instructions": "根据提供的信息，使用contact_info_generator工具生成特定格式的联系方式。",
  "tools": [{
    "name": "contact_info_generator",
    "type": "custom",
    "description": "生成符合特定格式的联系方式信息",
    "format": {
      "type": "grammar",
      "syntax": "regex",
      "definition": "^[A-Za-z\\s]{2,30}\\s*\\|\\s*\\+\\d{1,3}[-\\s]?\\d{3,4}[-\\s]?\\d{3,4}[-\\s]?\\d{3,4}\\s*\\|\\s*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}$"
    }
  }]
}
```

#### Lark语法格式工具
```json
{
  "model": "gpt-5-nano",
  "input": [{
    "type": "message",
    "role": "user",
    "content": "使用api_response_generator工具，随机生成一个符合要求的示例"
  }],
  "tools": [{
    "name": "api_response_generator",
    "type": "custom",
    "description": "生成符合特定JSON结构的API响应数据",
    "format": {
      "type": "grammar",
      "syntax": "lark",
      "definition": "start: response\n\nresponse: \"{\" \"\\\"status\\\":\" status \",\" \"\\\"code\\\":\" number \",\" \"\\\"data\\\":\" data_object \",\" \"\\\"message\\\":\" string \"}\"\n\nstatus: \"\\\"success\\\"\" | \"\\\"error\\\"\"\n\ndata_object: \"{\" \"\\\"id\\\":\" number \",\" \"\\\"name\\\":\" string \",\" \"\\\"items\\\":\" array \"}\"\n\narray: \"[\" [item (\",\" item)*] \"]\"\n\nitem: \"{\" \"\\\"title\\\":\" string \",\" \"\\\"count\\\":\" number \"}\"\n\nstring: \"\\\"\" /[^\"]*/ \"\\\"\"\n\nnumber: /\\d+/\n\n%import common.WS\n%ignore WS"
    }
  }]
}
```

### 6. 高级特性

#### 推理链（Reasoning）
```json
{
  "model": "gpt-5-nano",
  "input": "复杂推理问题...",
  "reasoning": {
    "effort": "high",
    "summary": "auto"
  }
}
```

#### 响应格式控制
```json
{
  "model": "gpt-5-nano",
  "input": "生成JSON格式的数据",
  "text": {
    "response_format": {
      "type": "json_schema",
      "json_schema": {
        "name": "response",
        "schema": {
          "type": "object",
          "properties": {
            "result": {"type": "string"},
            "confidence": {"type": "number"}
          },
          "required": ["result", "confidence"]
        }
      }
    }
  }
}
```

## 流式响应

启用流式响应后，响应以Server-Sent Events (SSE)格式返回：

```json
{
  "stream": true,      
  "background": false 
}
```

流式事件类型：
- `message_start`: 响应开始
- `content_block_start`: 内容块开始
- `content_block_delta`: 内容增量
- `content_block_stop`: 内容块结束
- `message_delta`: 消息元数据更新
- `message_stop`: 响应结束

## 存储控制

```json
{
  "store": false 
}
```

- `store: true`: 持久化存储对话
- `store: false`: 临时对话，不保存

## 错误处理

API使用标准HTTP状态码：
- `200 OK`: 成功
- `400 Bad Request`: 请求格式错误
- `401 Unauthorized`: 认证失败
- `429 Too Many Requests`: 速率限制
- `500 Internal Server Error`: 服务器错误

错误响应格式：
```json
{
  "error": {
    "message": "错误描述",
    "type": "error_type",
    "code": "error_code"
  }
}
```

## 最佳实践

1. **选择合适的模型**: 根据任务复杂度选择模型
2. **使用流式响应**: 提升用户体验，特别是长响应
3. **合理设置推理级别**: 简单任务用low，复杂推理用high
4. **工具并行调用**: 提高效率，减少延迟
5. **轻量级使用**: 如在client端实现对话管理，则使用`store: false`避免数据存储
6. **续接对话**: 使用`previous_response_id`保持上下文

## 示例场景

### 智能客服
```json
{
  "model": "gpt-5",
  "input": "查询我的订单状态",
  "tools": [{
    "type": "function",
    "name": "query_order",
    "description": "查询订单状态"
  }],
  "store": true,
  "stream": true
}
```

### 代码生成助手
```json
{
  "model": "gpt-5-nano",
  "input": "生成一个Python快速排序算法",
  "reasoning": {
    "effort": "medium"
  },
  "text": {
    "response_format": {
      "type": "code"
    }
  }
}
```

### 多模态分析
```json
{
  "model": "gpt-5",
  "input": [{
    "role": "user",
    "content": [
      {
        "type": "input_image",
        "image_url": "https://example.com/chart.png"
      },
      {
        "type": "input_text",
        "text": "分析这个图表的趋势"
      }
    ]
  }],
  "reasoning": {
    "effort": "high"
  }
}
```

## API vs Assistant API对比

| 特性 | Response API | Assistant API |
|-----|-------------|---------------|
| 使用场景 | 轻量对话、无状态交互 | 复杂智能体、有状态工作流 |
| 会话管理 | 灵活（可选持久化） | 强制Thread管理 |
| 配置方式 | 每次请求配置 | 预定义Assistant |
| 工具使用 | 即时定义 | 预配置+运行时覆盖 |
| 学习曲线 | 简单直接 | 需要理解Thread概念 |
| 性能 | 更轻量 | 额外管理开销 |
