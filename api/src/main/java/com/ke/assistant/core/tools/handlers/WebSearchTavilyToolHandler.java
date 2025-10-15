package com.ke.assistant.core.tools.handlers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.configuration.ToolProperties;
import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolHandler;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import com.ke.assistant.core.tools.ToolStreamEvent;
import com.ke.assistant.util.AnnotationUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.message.content.Annotation;
import com.theokanning.openai.response.ItemStatus;
import com.theokanning.openai.response.stream.WebSearchCompletedEvent;
import com.theokanning.openai.response.stream.WebSearchInProgressEvent;
import com.theokanning.openai.response.stream.WebSearchSearchingEvent;
import com.theokanning.openai.response.tool.WebSearchToolCall;
import com.theokanning.openai.web.WebSearchRequest;
import com.theokanning.openai.web.WebSearchResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Tavily搜索工具处理器
 */
@Slf4j
@Component
public class WebSearchTavilyToolHandler implements ToolHandler {
    
    @Autowired
    private AssistantProperties assistantProperties;

    @Autowired
    private OpenAiServiceFactory openAiServiceFactory;

    private ToolProperties.WebSearchTavilyToolProperties tavilyProperties;
    
    @PostConstruct
    public void init() {
        this.tavilyProperties = assistantProperties.getTools().getWebSearchTavily();
    }
    
    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        ItemStatus status = ItemStatus.IN_PROGRESS;
        WebSearchToolCall toolCall = new WebSearchToolCall();
        toolCall.setStatus(status);
        try {
            if(channel != null) {
                channel.output(context.getToolId(), context.getTool(), ToolStreamEvent.builder().toolCallId(context.getToolId())
                        .executionStage(ToolStreamEvent.ExecutionStage.prepare)
                        .result(toolCall)
                        .event(WebSearchInProgressEvent.builder().build())
                        .build());
            }
            if(tavilyProperties.getApiKey() == null) {
                throw new IllegalStateException("Tavily apikey is null");
            }

            // 解析参数
            String query = Optional.ofNullable(arguments.get("query")).map(Object::toString).orElse(null);
            if(query == null || query.trim().isEmpty()) {
                throw new IllegalArgumentException("query is null");
            }

            // 构建请求体
            WebSearchRequest searchRequest = buildSearchRequest(query);

            if(channel != null) {
                channel.output(context.getToolId(), context.getTool(), ToolStreamEvent.builder().toolCallId(context.getToolId())
                        .executionStage(ToolStreamEvent.ExecutionStage.processing)
                        .event(WebSearchSearchingEvent.builder().build())
                        .build());
            }

            WebSearchResponse response;
            if(StringUtils.isNotBlank(searchRequest.getModel())) {
                BellaContext.replace(context.getBellaContext());
                response = openAiServiceFactory.create().webSearch(searchRequest);
            } else {
                Request request = new Request.Builder()
                        .header("Authorization", "Bearer " + tavilyProperties.getApiKey())
                        .url(tavilyProperties.getUrl())
                        .post(RequestBody.create(JacksonUtils.serialize(searchRequest), okhttp3.MediaType.parse("application/json")))
                        .build();

                // 发送请求
                response = HttpUtils.httpRequest(request, WebSearchResponse.class, 30, 30);
            }

            Pair<Boolean, List<TavilySearchResult>> result = processSearchResults(response);

            // 构建输出内容
            String output = JacksonUtils.serialize(result.getRight());

            List<Annotation> annotations = result.getLeft() ? result.getRight().stream()
                    .map(searchResult -> AnnotationUtils.buildWebSearch(searchResult.url, searchResult.title))
                    .collect(Collectors.toList()) : new ArrayList<>();

            status = ItemStatus.COMPLETED;
            return new ToolResult(ToolResult.ToolResultType.text, output, annotations);
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            status = ItemStatus.INCOMPLETE;
            return new ToolResult(ToolResult.ToolResultType.text, e.getMessage(), new ArrayList<>());
        } finally {
            BellaContext.clearAll();
            toolCall.setStatus(status);
            if(channel != null) {
                channel.output(context.getToolId(), context.getTool(), ToolStreamEvent.builder().toolCallId(context.getToolId())
                        .executionStage(ToolStreamEvent.ExecutionStage.completed)
                        .event(WebSearchCompletedEvent.builder().build())
                        .result(toolCall)
                        .build());
            }
        }
    }
    
    /**
     * 构建搜索请求
     */
    private WebSearchRequest buildSearchRequest(String query) {
        WebSearchRequest request = new WebSearchRequest();
        request.setQuery(query);
        request.setSearchDepth("advanced".equals(tavilyProperties.getSearchDepth()) ? WebSearchRequest.SearchDepth.ADVANCED : WebSearchRequest.SearchDepth.BASIC);
        request.setIncludeRawContent(tavilyProperties.isIncludeRawContent());
        request.setMaxResults(tavilyProperties.getMaxResults());
        request.setModel(tavilyProperties.getBellaModel());
        return request;
    }
    
    /**
     * 处理搜索结果
     */
    private Pair<Boolean, List<TavilySearchResult>> processSearchResults(WebSearchResponse response) {
        List<TavilySearchResult> resultData = new ArrayList<>();

        if(response.getResults() != null) {
            for (WebSearchResponse.SearchResult result : response.getResults()) {
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
            return Pair.of(false, resultData);
        }

        return Pair.of(true, resultData);
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
