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
 * 网页爬虫工具处理器
 */
@Component
public class WebCrawlerToolHandler implements ToolHandler {
    
    @Autowired
    private AssistantProperties assistantProperties;
    
    private ToolProperties.WebCrawlerToolProperties webCrawlerProperties;
    
    @PostConstruct
    public void init() {
        this.webCrawlerProperties = assistantProperties.getTools().getWebCrawler();
    }

    @SuppressWarnings("unchecked")
    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {

        // 解析参数
        List<String> urls = (List<String>) Optional.ofNullable(arguments.get("web_crawler_urls")).orElse(new ArrayList<>());

        if(urls.isEmpty()) {
            throw new IllegalArgumentException("urls is null or not array");
        }

        List<WebCrawlerResult> webCrawlerUrlContent = new ArrayList<>();
        // 循环处理每个URL
        WebCrawlerRequest crawlerRequest = buildCrawlerRequest(urls);

        Request request = new Request.Builder()
                .url(webCrawlerProperties.getUrl())
                .addHeader("Authorization", "Bearer " + webCrawlerProperties.getApiKey())
                .post(RequestBody.create(JacksonUtils.serialize(crawlerRequest), okhttp3.MediaType.parse("application/json")))
                .build();

        // 发送请求
        WebCrawlerResponse response = HttpUtils.httpRequest(request, WebCrawlerResponse.class, 30, webCrawlerProperties.getTimeout());

        // 处理响应
        WebCrawlerResult urlResult = new WebCrawlerResult();
        urlResult.setUrl(urls);

        if(response.getResults() != null && !response.getResults().isEmpty()) {
            urlResult.setContent(response.getResults());
            webCrawlerUrlContent.add(urlResult);
        } else if(response.getFailedResults() != null) {
            urlResult.setContent(response.getFailedResults());
            webCrawlerUrlContent.add(urlResult);
        }

        // 构建输出内容
        String output = JacksonUtils.serialize(webCrawlerUrlContent);

        // 直接返回爬取结果
        return ToolResult.builder().output(output).build();
    }
    
    /**
     * 构建爬虫请求
     */
    private WebCrawlerRequest buildCrawlerRequest(List<String> url) {
        WebCrawlerRequest request = new WebCrawlerRequest();
        request.setUrls(url);
        request.setIncludeImages(false);
        request.setExtractDepth("advanced");
        return request;
    }
    
    @Override
    public String getToolName() {
        return "web_crawler";
    }
    
    @Override
    public String getDescription() {
        return "批量爬取网页内容，提取网页文本信息";
    }
    
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> urlsParam = new HashMap<>();
        urlsParam.put("type", "array");
        Map<String, Object> items = new HashMap<>();
        items.put("type", "string");
        urlsParam.put("items", items);
        urlsParam.put("description", "要爬取的网页URL列表");
        properties.put("web_crawler_urls", urlsParam);
        
        parameters.put("properties", properties);
        parameters.put("required", Lists.newArrayList("web_crawler_urls"));
        
        return parameters;
    }
    
    @Override
    public boolean isFinal() {
        return false;
    }
    
    // 请求实体类
    @Data
    public static class WebCrawlerRequest {
        private List<String> urls;
        @JsonProperty("include_images")
        private boolean includeImages;
        @JsonProperty("extract_depth")
        private String extractDepth;
    }
    
    // 响应实体类
    @Data
    public static class WebCrawlerResponse {
        private List<Object> results;
        @JsonProperty("failed_results")
        private List<Object> failedResults;
    }


    @Data
    public static class WebCrawlerResult {
        private List<String> url;
        private List<Object> content;
    }
}
