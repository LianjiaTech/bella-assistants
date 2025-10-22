package com.ke.assistant.core.tools.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import com.ke.assistant.core.tools.ToolHandler;
import com.ke.bella.openapi.BellaContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.configuration.ToolProperties;
import com.ke.assistant.core.log.RunLogger;
import com.ke.assistant.core.tools.SseConverter;
import com.ke.assistant.core.tools.ToolCallListener;
import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.assistant.Tool;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * RAG检索增强生成工具处理器 - Bella RAG
 */
@Slf4j
@Component
public class RagToolHandler implements ToolHandler {
    
    @Autowired
    private AssistantProperties assistantProperties;

    @Autowired
    private RunLogger runLogger;
    
    private ToolProperties.RagToolProperties ragProperties;
    
    @PostConstruct
    public void init() {
        this.ragProperties = assistantProperties.getTools().getRag();
    }
    
    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {

        String query = Optional.ofNullable(arguments.get("query")).map(Object::toString).orElse(null);
        // 解析参数
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query is null");
        }

        
        // 构建请求体
        RagRequest requestBody = buildRequestBody(query, context);

        runLogger.log("rag_request", BellaContext.snapshot(), JacksonUtils.toMap(requestBody));

        // 构建请求
        Request request = new Request.Builder()
                .url(ragProperties.getUrl())
                .addHeader("Accept", "text/event-stream")
                .addHeader("Cache-Control", "no-cache")
                .post(RequestBody.create(JacksonUtils.serialize(requestBody), MediaType.parse("application/json")))
                .build();
        
        // 创建SSE事件监听器，用于处理流式响应
        ToolCallListener listener = new ToolCallListener(
            context.getToolId(),
            channel,
            new RagSseConverter(),
            ragProperties.isFinal()
        );
        
        try {
            // 使用HttpUtils发送SSE流式请求
            HttpUtils.streamRequest(request, listener);
            
            // 获取完整输出
            String output = listener.getOutput();
            
            return new ToolResult(ToolResult.ToolResultType.text, output);
                
        } catch (Exception e) {
            log.error("RAG tool execution failed", e);
            throw new RuntimeException("RAG tool execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 构建请求体
     */
    private RagRequest buildRequestBody(String query, ToolContext context) {

        Tool.Rag tool = (Tool.Rag)context.getTool();

        RagRequest request = new RagRequest();
        request.setQuery(query);
        request.setUser(context.getUser());
        request.setStream(true);
        
        // 构建检索参数
        RetrievalParam retrievalParam = new RetrievalParam();
        retrievalParam.setScore(tool.getDefaultMetadata().getScore());
        retrievalParam.setTopK(tool.getDefaultMetadata().getTopK());
        retrievalParam.setFileIds(context.getFiles());
        retrievalParam.setMetadataFilter(tool.getDefaultMetadata().getMetadataFilter());
        retrievalParam.setRetrievalMode(tool.getDefaultMetadata().getRetrieveMode());
        retrievalParam.setPlugins(tool.getDefaultMetadata().getPlugins());

        retrievalParam.setTopK(tool.getDefaultMetadata().getTopK());
        retrievalParam.setScore(tool.getDefaultMetadata().getScore());
        
        // 构建生成参数
        GenerateParam generateParam = new GenerateParam();
        generateParam.setInstructions(tool.getDefaultMetadata().getInstructions());
        generateParam.setModel(ragProperties.getModel());
        
        request.setRetrievalParam(retrievalParam);
        request.setGenerateParam(generateParam);
        
        return request;
    }
    
    @Override
    public String getToolName() {
        return "rag";
    }
    
    @Override
    public String getDescription() {
        return "可以用来查询已上传到这个助手的信息。如果用户正在引用特定的文件，那通常是一个很好的提示，这里可能有他们需要的信息。请提取完整的输入、不要提关键词。";
    }
    
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> queryParam = new HashMap<>();
        queryParam.put("type", "string");
        queryParam.put("description", "检索中输入的检索语句");
        properties.put("query", queryParam);

        parameters.put("properties", properties);
        parameters.put("required", Lists.newArrayList("query"));

        return parameters;
    }
    
    @Override
    public boolean isFinal() {
        return ragProperties.isFinal();
    }
    
    /**
     * RAG SSE消息转换器，按照Python RAG工具的处理逻辑
     */
    private static class RagSseConverter implements SseConverter {

        @Override
        public String convert(String eventType, String msg) {
            try {
                // 处理各种事件类型，按照Python代码的逻辑
                switch (eventType) {
                    case "error":
                        // 错误事件，直接抛出异常
                        throw new RuntimeException("RAG service error: " + msg);

                    case "retrieval.completed":
                        // 检索完成事件，不输出内容，只做数据处理
                        // todo: 保存file_annotations_metadata，用于显示文档的引用
                        return null;

                    case "message.delta":
                        // 消息增量事件，这是主要的内容输出
                        return parseMessageDelta(msg);

                    case "message.sensitives":
                        // 敏感信息事件，不输出内容
                        return null;

                    case "done":
                        // 完成事件，不输出内容
                        return null;

                    default:
                        // 未知事件类型，不输出
                        return null;
                }
            } catch (Exception e) {
                log.warn("Failed to parse RAG SSE message, eventType: {}, msg: {}", eventType, msg, e);
                throw new RuntimeException(e.getMessage());
            }
        }

        /**
         * 解析message.delta事件，按照Python代码的逻辑
         */
        private String parseMessageDelta(String msg) {
            try {
                MessageDeltaEvent event = JacksonUtils.deserialize(msg, MessageDeltaEvent.class);
                if (event.getDelta() == null) {
                    return "";
                }

                List<DeltaItem> deltas = event.getDelta();
                if (deltas.isEmpty()) {
                    return "";
                }

                // 拼接内容：[d["text"]["value"] if "text" in d else d["value"] for d in deltas]
                StringBuilder content = new StringBuilder();
                for (DeltaItem delta : deltas) {
                    if (delta.getText() != null && delta.getText().getValue() != null) {
                        content.append(delta.getText().getValue());
                    } else if (delta.getValue() != null) {
                        content.append(delta.getValue());
                    }
                }

                return content.toString();

            } catch (Exception e) {
                log.warn("Failed to parse message.delta: {}", msg, e);
                return "";
            }
        }
    }
    
    // 请求相关实体类
    @Data
    public static class RagRequest {
        private String query;
        private String user;
        private boolean stream;
        @JsonProperty("retrieval_param")
        private RetrievalParam retrievalParam;
        @JsonProperty("generate_param")
        private GenerateParam generateParam;
    }
    
    @Data
    public static class RetrievalParam {
        @JsonProperty("file_ids")
        private List<String> fileIds;
        @JsonProperty("top_k")
        private int topK;
        private double score;
        @JsonProperty("metadata_filter")
        private List<Map<String, Object>> metadataFilter;
        @JsonProperty("retrieval_mode")
        private String retrievalMode;
        private List<Map<String, Object>> plugins;
    }
    
    @Data
    public static class GenerateParam {
        private String instructions;
        private String model;
    }

    // SSE消息相关实体类
    @Data
    public static class MessageDeltaEvent {
        private List<DeltaItem> delta;
    }
    
    @Data
    public static class DeltaItem {
        private String value;
        private TextContent text;
    }
    
    @Data
    public static class TextContent {
        private String value;
    }
}
