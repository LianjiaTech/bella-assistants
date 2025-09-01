package com.ke.assistant.core.tools.handlers;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 天气搜索工具处理器
 */
@Component
public class WeatherSearchToolHandler implements ToolHandler {
    
    @Autowired
    private AssistantProperties assistantProperties;
    
    private ToolProperties.WeatherSearchToolProperties weatherProperties;
    
    @PostConstruct
    public void init() {
        this.weatherProperties = assistantProperties.getTools().getWeatherSearch();
    }
    
    @Override
    public ToolResult execute(ToolContext context, JsonNode arguments, ToolOutputChannel channel) {

        if(weatherProperties.getApiKey() == null) {
            throw new IllegalStateException("apikey is null");
        }
        
        // 解析参数
        String city = arguments.get("city").asText();
        
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("city is null");
        }
        
        // 构建请求URL
        String url = weatherProperties.getUrl() + "?key=" + weatherProperties.getApiKey() + "&city=" + city + "&output=JSON&extensions=all";
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        // 发送请求
        WeatherResponse response = HttpUtils.httpRequest(request, WeatherResponse.class, 30, 30);
        
        // 处理响应数据
        Object weatherData = processWeatherData(response);
        
        // 构建输出内容
        String output = weatherData instanceof String ? (String) weatherData : JacksonUtils.serialize(weatherData);
        
        return ToolResult.builder().output(output).build();
    }
    
    /**
     * 处理天气数据
     */
    private Object processWeatherData(WeatherResponse response) {
        if (response.getForecasts() != null && !response.getForecasts().isEmpty()) {
            WeatherForecast forecast = response.getForecasts().get(0);
            return forecast.getCasts();
        } else {
            return "未查到该城市，换个城市试试";
        }
    }
    
    @Override
    public String getToolName() {
        return "weather_search";
    }
    
    @Override
    public String getDescription() {
        return "查询指定城市的天气信息，返回详细的天气预报数据";
    }
    
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> cityParam = new HashMap<>();
        cityParam.put("type", "string");
        cityParam.put("description", "要查询天气的城市名称");
        properties.put("city", cityParam);
        
        parameters.put("properties", properties);
        parameters.put("required", Lists.newArrayList("city"));
        
        return parameters;
    }
    
    @Override
    public boolean isFinal() {
        return false;
    }
    
    // 天气响应实体类
    @Data
    public static class WeatherResponse {
        private List<WeatherForecast> forecasts;
    }
    
    // 天气预报实体类
    @Data
    public static class WeatherForecast {
        private List<Object> casts;
    }
}
