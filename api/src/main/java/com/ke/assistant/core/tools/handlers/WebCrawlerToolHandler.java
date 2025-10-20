package com.ke.assistant.core.tools.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.Lists;
import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.configuration.ToolProperties;
import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolHandler;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.web.WebExtractRequest;
import com.theokanning.openai.web.WebExtractResponse;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * 网页爬虫工具处理器
 */
@Slf4j
@Component
public class WebCrawlerToolHandler implements ToolHandler {
    
    @Autowired
    private AssistantProperties assistantProperties;
    
    private ToolProperties.WebCrawlerToolProperties webCrawlerProperties;

    @Autowired
    private OpenAiServiceFactory openAiServiceFactory;
    
    @PostConstruct
    public void init() {
        this.webCrawlerProperties = assistantProperties.getTools().getWebCrawler();
    }

    @SuppressWarnings("unchecked")
    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        try {
            // 解析参数
            List<String> urls = (List<String>) Optional.ofNullable(arguments.get("web_crawler_urls")).orElse(new ArrayList<>());

            if(urls.isEmpty()) {
                throw new IllegalArgumentException("urls is null or not array");
            }

            List<WebCrawlerResult> webCrawlerUrlContent = new ArrayList<>();
            // 循环处理每个URL
            WebExtractRequest crawlerRequest = buildCrawlerRequest(urls);

            WebExtractResponse response;

            if(StringUtils.isNotBlank(crawlerRequest.getModel())) {
                BellaContext.replace(context.getBellaContext());
                response = openAiServiceFactory.create().webExtract(crawlerRequest);
            } else {
                Request request = new Request.Builder()
                        .url(webCrawlerProperties.getUrl())
                        .addHeader("Authorization", "Bearer " + webCrawlerProperties.getApiKey())
                        .post(RequestBody.create(JacksonUtils.serialize(crawlerRequest), okhttp3.MediaType.parse("application/json")))
                        .build();
                // 发送请求
                response = HttpUtils.httpRequest(request, WebExtractResponse.class, 30, webCrawlerProperties.getTimeout());
            }

            // 处理响应
            WebCrawlerResult urlResult = new WebCrawlerResult();

            if(response.getResults() != null && !response.getResults().isEmpty()) {
                webCrawlerUrlContent = response.getResults().stream().map(result -> {
                    WebCrawlerResult webCrawlerResult = new WebCrawlerResult();
                    webCrawlerResult.setUrl(result.getUrl());
                    webCrawlerResult.setContent(result.getRawContent());
                    return webCrawlerResult;
                }).toList();
            } else if(response.getFailedResults() != null) {
                urlResult.setFailedResult(response.getFailedResults());
                webCrawlerUrlContent.add(urlResult);
            }

            // 构建输出内容
            String output = JacksonUtils.serialize(webCrawlerUrlContent);

            // 直接返回爬取结果
            return new ToolResult(ToolResult.ToolResultType.text, output);
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            return new ToolResult(e.getMessage());
        } finally {
            BellaContext.clearAll();
        }
    }
    
    /**
     * 构建爬虫请求
     */
    private WebExtractRequest buildCrawlerRequest(List<String> url) {
        WebExtractRequest request = new WebExtractRequest();
        request.setUrls(url);
        request.setIncludeImages(false);
        request.setExtractDepth(WebExtractRequest.ExtractDepth.ADVANCED);
        request.setModel(webCrawlerProperties.getBellaModel());
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


    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WebCrawlerResult {
        private String url;
        private String content;
        private List<WebExtractResponse.FailedResult> failedResult;
    }
}
