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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 维基百科搜索工具处理器
 */
@Component
public class WikiSearchToolHandler implements ToolHandler {
    
    @Autowired
    private AssistantProperties assistantProperties;
    
    private ToolProperties.WikiSearchToolProperties wikiProperties;
    
    @PostConstruct
    public void init() {
        this.wikiProperties = assistantProperties.getTools().getWikiSearch();
    }
    
    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {

        // 解析参数
        String query = Optional.ofNullable(arguments.get("query")).map(Object::toString).orElse(null);
        if(query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query is null");
        }

        WikiSearchRequest searchRequest = buildSearchRequest(query);

        Request request = new Request.Builder()
                .url(searchRequest.getUrl())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(JacksonUtils.serialize(searchRequest), MediaType.parse("application/json")))
                .build();

        // 发送请求
        WikiSearchResponse response = HttpUtils.httpRequest(request, WikiSearchResponse.class, 30, 30);

        // 构建输出内容
        String output = JacksonUtils.serialize(response.getData());

        // 直接返回搜索结果数据
        return ToolResult.builder().output(output).build();
    }
    
    /**
     * 构建搜索请求
     */
    private WikiSearchRequest buildSearchRequest(String query) {
        WikiSearchRequest request = new WikiSearchRequest();
        request.setInput("维基搜索" + query);
        request.setUrl(wikiProperties.getUrl());
        return request;
    }
    
    @Override
    public String getToolName() {
        return "wiki_search";
    }
    
    @Override
    public String getDescription() {
        return "一个用于执行维基百科搜索并提取片段和网页的工具";
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
    public static class WikiSearchRequest {
        private String input;
        private String url;
    }
    
    // 响应实体类
    @Data
    public static class WikiSearchResponse {
        private List<WikiSearchItem> data;
    }
    
    // 搜索结果项
    @Data
    public static class WikiSearchItem {
        private String Title;
        private String Result;
        private String URL;
    }
}
