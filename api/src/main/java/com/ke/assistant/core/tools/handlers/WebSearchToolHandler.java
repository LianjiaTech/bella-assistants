package com.ke.assistant.core.tools.handlers;

import com.google.common.collect.Lists;
import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.configuration.ToolProperties;
import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolHandler;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.Data;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 网页搜索工具处理器
 */
@Component
public class WebSearchToolHandler implements ToolHandler {
    
    @Autowired
    private AssistantProperties assistantProperties;
    
    private ToolProperties.WebSearchToolProperties webSearchProperties;
    
    @PostConstruct
    public void init() {
        this.webSearchProperties = assistantProperties.getTools().getWebSearch();
    }
    
    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {

        // 解析参数
        String query = Optional.ofNullable(arguments.get("query")).map(Object::toString).orElse(null);

        if(query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query is null");
        }

        WebSearchRequest searchRequest = buildSearchRequest(query);

        Request request = new Request.Builder()
                .url(webSearchProperties.getUrl())
                .post(RequestBody.create(JacksonUtils.serialize(searchRequest), MediaType.parse("application/json")))
                .build();

        // 发送请求
        WebSearchResponse response = HttpUtils.httpRequest(request, WebSearchResponse.class, 30, 30);

        List<WebSearchResult> resultData = processSearchResults(response);

        // 构建输出内容
        String output = JacksonUtils.serialize(resultData);

        // 直接返回搜索结果
        return ToolResult.builder().output(output).build();
    }
    
    /**
     * 构建搜索请求
     */
    private WebSearchRequest buildSearchRequest(String query) {
        WebSearchRequest request = new WebSearchRequest();
        request.setInput(query);
        return request;
    }
    
    /**
     * 处理搜索结果
     */
    private List<WebSearchResult> processSearchResults(WebSearchResponse response) {
        List<WebSearchResult> resultData = new ArrayList<>();

        if(response.getData() != null) {
            for (String item : response.getData()) {
                String[] parts = item.split("\n");
                if(parts.length >= 3) {
                    WebSearchResult resultItem = new WebSearchResult();
                    resultItem.setTitle(parts[0]);
                    resultItem.setResult(parts[1]);
                    resultItem.setUrl(parts[2]);
                    resultData.add(resultItem);
                }
            }
        }
        
        return resultData;
    }
    
    @Override
    public String getToolName() {
        return "web_search";
    }
    
    @Override
    public String getDescription() {
        return "一个用于搜索并提取片段和网页的工具,不搜索维基百科";
    }
    
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> queryParam = new HashMap<>();
        queryParam.put("type", "string");
        queryParam.put("description", "需要查询的内容");
        properties.put("query", queryParam);
        
        parameters.put("properties", properties);
        parameters.put("required", Lists.newArrayList("query"));
        
        return parameters;
    }
    
    @Override
    public boolean isFinal() {
        return false;
    }
    
    // 请求实体类
    @Data
    public static class WebSearchRequest {
        private String input;
    }
    
    // 响应实体类
    @Data
    public static class WebSearchResponse {
        private List<String> data;
    }
    
    // 搜索结果实体类
    @Data
    public static class WebSearchResult {
        private String title;
        private String result;
        private String url;
    }
}
