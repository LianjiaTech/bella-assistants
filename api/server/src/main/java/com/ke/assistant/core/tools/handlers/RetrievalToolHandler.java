package com.ke.assistant.core.tools.handlers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.configuration.ToolProperties;
import com.ke.assistant.core.tools.BellaToolHandler;
import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.assistant.Tool;
import lombok.Data;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档检索工具处理器
 */
@Component
public class RetrievalToolHandler implements BellaToolHandler {
    
    @Autowired
    private AssistantProperties assistantProperties;
    
    private ToolProperties.RetrievalToolProperties retrievalProperties;
    
    @PostConstruct
    public void init() {
        this.retrievalProperties = assistantProperties.getTools().getRetrieval();
    }
    
    @Override
    public ToolResult doExecute(ToolContext context, JsonNode arguments, ToolOutputChannel channel) {

        // 解析参数并构建请求体
        String query = arguments.get("query").asText();
        if(query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query is null");
        }

        RetrievalRequest requestBody = buildRequestBody(query, context);

        Request request = new Request.Builder()
                .url(retrievalProperties.getUrl())
                .post(RequestBody.create(JacksonUtils.serialize(requestBody), okhttp3.MediaType.parse("application/json")))
                .build();

        // 发送请求
        RetrievalResponse response = HttpUtils.httpRequest(request, RetrievalResponse.class);

        // 构建返回结果
        buildRetrievalResponse(response);

        if(isFinal()) {
            channel.output(context.getToolId(), response.getValue());
        }

        return ToolResult.builder().output(response.getValue()).build();
    }
    
    /**
     * 构建请求体
     */
    private RetrievalRequest buildRequestBody(String query, ToolContext context) {
        Tool.Retrieval tool = (Tool.Retrieval)context.getTool();
        RetrievalRequest request = new RetrievalRequest();
        request.setQuery(query);
        request.setFileIds(context.getFiles());
        request.setTopK(tool.getDefaultMetadata().getTopK());
        request.setScore(tool.getDefaultMetadata().getScore());
        request.setUser(context.getUser());
        request.setMetadataFilter(tool.getDefaultMetadata().getMetadataFilter());
        request.setRetrievalMode(tool.getDefaultMetadata().getRetrieveMode());
        return request;
    }
    
    /**
     * 构建响应
     */
    private void buildRetrievalResponse(RetrievalResponse response) {
        // 处理空结果的情况
        if (response.getList() == null || response.getList().isEmpty()) {
            // 创建默认的空chunk
            Chunk emptyChunk = new Chunk();
            emptyChunk.setId("");
            emptyChunk.setFileId("");
            emptyChunk.setFileName("");
            emptyChunk.setScore(0.0);
            emptyChunk.setChunkId("");
            emptyChunk.setContent("没有检索出符合 score 的数据");
            emptyChunk.setFileTag("");

            response.setList(Lists.newArrayList(emptyChunk));
        }
        // 设置value为第一个chunk的content
        response.setValue(response.getList().get(0).getContent());
    }
    
    @Override
    public String getToolName() {
        return "retrieval";
    }
    
    @Override
    public String getDescription() {
        return "从文档库中检索相关内容";
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
        return true;
    }
    
    // 请求实体类
    @Data
    public static class RetrievalRequest {
        private String query;
        @JsonProperty("file_ids")
        private List<String> fileIds;
        @JsonProperty("top_k")
        private int topK;
        private double score;
        private String user;
        @JsonProperty("metadata_filter")
        private List<Map<String, Object>> metadataFilter;
        @JsonProperty("retrieval_mode")
        private String retrievalMode;
    }
    
    // 响应实体类
    @Data
    public static class RetrievalResponse {
        private String id;
        @JsonProperty("create_at")
        private long createdAt;
        private String object;
        private String value;
        private List<Chunk> list;
    }
    
    // 文档块实体类
    @Data
    public static class Chunk {
        private String id;
        @JsonProperty("file_id")
        private String fileId;
        @JsonProperty("file_name")
        private String fileName;
        private Double score;
        @JsonProperty("chunk_id")
        private String chunkId;
        private String content;
        @JsonProperty("file_tag")
        private String fileTag;
    }
}
