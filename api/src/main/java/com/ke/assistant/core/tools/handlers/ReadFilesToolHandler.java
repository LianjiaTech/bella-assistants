package com.ke.assistant.core.tools.handlers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.configuration.ToolProperties;
import com.ke.assistant.core.tools.BellaToolHandler;
import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.Data;
import okhttp3.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文件读取工具处理器
 */
@Component
public class ReadFilesToolHandler extends BellaToolHandler {
    
    @Autowired
    private AssistantProperties assistantProperties;
    
    private ToolProperties.ReadFilesToolProperties readFilesProperties;
    
    @PostConstruct
    public void init() {
        this.readFilesProperties = assistantProperties.getTools().getReadFiles();
    }

    @SuppressWarnings("unchecked")
    @Override
    public ToolResult doExecute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {

        // 解析参数
        List<String> fileIds = (List<String>) Optional.ofNullable(arguments.get("file_ids")).orElse(new ArrayList<>());
        if(fileIds.isEmpty()) {
            throw new IllegalArgumentException("file_ids is null");
        }

        Map<String, String> filesContent = new HashMap<>();

        // 循环处理每个文件ID
        for (String fileId : fileIds) {
            ReadFileResponse response = readSingleFile(fileId);
            String content = processFileResponse(fileId, response);
            filesContent.put(fileId, content);
        }

        String output = JacksonUtils.serialize(filesContent);
        return new ToolResult(ToolResult.ToolResultType.text, output);
    }

    
    /**
     * 读取单个文件
     */
    private ReadFileResponse readSingleFile(String fileId) {
        String url = readFilesProperties.getUrl() + fileId + "?parse_type=markdown";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        return HttpUtils.httpRequest(request, ReadFileResponse.class);
    }
    
    /**
     * 处理文件响应
     */
    private String processFileResponse(String fileId, ReadFileResponse response) {
        if(response.getDetail() != null && !response.getDetail().isEmpty()) {
            return "文件id" + fileId + "读取失败: " + response.getDetail();
        } else if(response.getMarkdownResult() != null) {
            return response.getMarkdownResult();
        } else {
            return "文件内容为空";
        }
    }
    
    @Override
    public String getToolName() {
        return "read_files";
    }
    
    @Override
    public String getDescription() {
        return "根据文件ids 获取文件内容，并将文件解析为markdown格式。如果不清楚应该使用哪些文件，可以输入全部的文件ID。";
    }
    
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> fileIdsParam = new HashMap<>();
        fileIdsParam.put("type", "array");
        Map<String, Object> items = new HashMap<>();
        items.put("type", "string");
        fileIdsParam.put("items", items);
        fileIdsParam.put("description", "要读取的文件ID列表");
        properties.put("file_ids", fileIdsParam);
        
        parameters.put("properties", properties);
        parameters.put("required", Lists.newArrayList("file_ids"));
        
        return parameters;
    }
    
    @Override
    public boolean isFinal() {
        return false;
    }
    
    // 文件读取响应实体类
    @Data
    public static class ReadFileResponse {
        private String detail;     // 错误信息
        @JsonProperty("markdown_result")
        private String markdownResult;   // 文件内容(markdown格式)
    }
}
