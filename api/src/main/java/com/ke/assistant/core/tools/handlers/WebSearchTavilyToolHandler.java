package com.ke.assistant.core.tools.handlers;

import com.fasterxml.jackson.annotation.JsonProperty;
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
 * Tavily搜索工具处理器
 */
@Component
public class WebSearchTavilyToolHandler implements ToolHandler {
    
    @Autowired
    private AssistantProperties assistantProperties;
    
    private ToolProperties.WebSearchTavilyToolProperties tavilyProperties;
    
    @PostConstruct
    public void init() {
        this.tavilyProperties = assistantProperties.getTools().getWebSearchTavily();
    }
    
    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {

        if(tavilyProperties.getApiKey() == null) {
            throw new IllegalStateException("Tavily apikey is null");
        }

        // 解析参数
        String query = Optional.ofNullable(arguments.get("query")).map(Object::toString).orElse(null);
        if(query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query is null");
        }

        // 构建请求体
        TavilySearchRequest searchRequest = buildSearchRequest(query);

        Request request = new Request.Builder()
                .header("Authorization", "Bearer " + tavilyProperties.getApiKey())
                .url(tavilyProperties.getUrl())
                .post(RequestBody.create(JacksonUtils.serialize(searchRequest), okhttp3.MediaType.parse("application/json")))
                .build();

        // 发送请求
        TavilySearchResponse response = HttpUtils.httpRequest(request, TavilySearchResponse.class, 30, 30);

        List<TavilySearchResult> resultData = processSearchResults(response);

        // 构建输出内容
        String output = JacksonUtils.serialize(resultData);

        return new ToolResult(ToolResult.ToolResultType.text, output);
    }
    
    /**
     * 构建搜索请求
     */
    private TavilySearchRequest buildSearchRequest(String query) {
        TavilySearchRequest request = new TavilySearchRequest();
        request.setQuery(query);
        request.setSearchDepth(tavilyProperties.getSearchDepth());
        request.setIncludeRawContent(tavilyProperties.isIncludeRawContent());
        request.setMaxResults(tavilyProperties.getMaxResults());
        return request;
    }
    
    /**
     * 处理搜索结果
     */
    private List<TavilySearchResult> processSearchResults(TavilySearchResponse response) {
        List<TavilySearchResult> resultData = new ArrayList<>();

        if(response.getResults() != null) {
            for (TavilyResult result : response.getResults()) {
                TavilySearchResult resultItem = new TavilySearchResult();
                resultItem.setTitle(result.getTitle());
                resultItem.setResult(result.getContent());
                resultItem.setUrl(result.getUrl());
                resultData.add(resultItem);
            }
        }

        // 如果没有结果
        if(resultData.isEmpty()) {
            TavilySearchResult errorResult = new TavilySearchResult();
            errorResult.setTitle("暂时无法请求");
            errorResult.setResult("暂时无法请求");
            errorResult.setUrl("暂时无法请求");
            resultData.add(errorResult);
        }
        
        return resultData;
    }
    
    @Override
    public String getToolName() {
        return "web_search_tavily";
    }
    
    @Override
    public String getDescription() {
        return "一个使用Tavily搜索引擎搜索网页内容并提取片段和网页的工具,不搜索维基百科";
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
    public static class TavilySearchRequest {
        private String query;
        @JsonProperty("search_depth")
        private String searchDepth;
        @JsonProperty("include_raw_content")
        private boolean includeRawContent;
        @JsonProperty("max_results")
        private int maxResults;
    }
    
    // 响应实体类
    @Data
    public static class TavilySearchResponse {
        private List<TavilyResult> results;
    }
    
    // Tavily搜索结果项
    @Data
    public static class TavilyResult {
        private String title;
        private String content;
        private String url;
    }
    
    // 输出结果实体类
    @Data
    public static class TavilySearchResult {
        @JsonProperty("Title")
        private String title;
        @JsonProperty("Result")
        private String result;
        @JsonProperty("URL")
        private String url;
    }
}
