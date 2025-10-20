package com.ke.assistant.core.tools.handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.configuration.ToolProperties;
import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolHandler;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import com.ke.assistant.service.ChartService;
import com.ke.assistant.service.S3Service;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.completion.chat.ImageUrl;

import lombok.extern.slf4j.Slf4j;

/**
 * 饼图生成工具处理器
 */
@Slf4j
@Component
public class PieToolHandler implements ToolHandler {
    
    @Autowired
    private AssistantProperties assistantProperties;
    
    @Autowired
    private ChartService chartService;
    
    @Autowired
    private S3Service s3Service;
    
    private ToolProperties.PieToolProperties pieProperties;
    
    @PostConstruct
    public void init() {
        this.pieProperties = assistantProperties.getTools().getPieTool();
    }
    
    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        
        // 解析参数
        String data = Optional.ofNullable(arguments.get("data")).map(Object::toString).orElse(null);
        if (data == null || data.trim().isEmpty()) {
            // 返回错误消息
            Map<String, Object> outputData = new HashMap<>();
            outputData.put("type", "text");
            outputData.put("message", "please input data");
            Map<String, String> metas = new HashMap<>();
            outputData.put("meta", metas);
            
            return new ToolResult(JacksonUtils.serialize(outputData));
        }
        
        // 解析数据
        String[] dataArray = data.split(";");
        double[] values = new double[dataArray.length];
        
        // 数据类型转换
        for (int i = 0; i < dataArray.length; i++) {
            String item = dataArray[i].trim();
            if (item.contains(".")) {
                values[i] = Double.parseDouble(item);
            } else {
                values[i] = Integer.parseInt(item);
            }
        }

        String categories = Optional.ofNullable(arguments.get("categories")).map(Object::toString).orElse(null);
        // 解析分类标签
        String[] categoryLabels = null;
        if (categories != null && !categories.trim().isEmpty()) {
            categoryLabels = categories.split(";");
            if (categoryLabels.length != values.length) {
                categoryLabels = null; // 长度不匹配时忽略标签
            }
        }
        
        // 检查S3服务是否配置
        if (!s3Service.isConfigured()) {
            return new ToolResult(JacksonUtils.serialize("S3存储服务未配置，无法生成饼图。"));
        }

        String title = Optional.ofNullable(arguments.get("title")).map(Object::toString).orElse("饼图");

        String imageUrl;
        try {
            // 使用JFreeChart生成饼图
            byte[] chartBytes = chartService.generatePieChart(
                title,                    // 图表标题
                values,                   // 数据数组
                categoryLabels,           // 分类标签数组
                pieProperties.getWidth(),    // 宽度
                pieProperties.getHeight()    // 高度
            );
            
            // 上传到S3（必需）
            imageUrl = s3Service.uploadChart(chartBytes, "jpg");
            log.info("Generated pie chart with {} data points and uploaded to S3: {}", values.length, imageUrl);
            
        } catch (Exception e) {
            log.error("Failed to generate pie chart or upload to S3", e);
            
            // 返回错误消息
            return new ToolResult("生成饼图或上传到S3失败: " + e.getMessage());
        }
        
        // 构建返回结果
        ImageUrl output = new ImageUrl();
        output.setUrl(imageUrl);

        if(isFinal()) {
            channel.output(context.getToolId(), output);
        }

        return new ToolResult(ToolResult.ToolResultType.image_url, imageUrl);
    }
    
    @Override
    public String getToolName() {
        return "generate_pie";
    }
    
    @Override
    public String getDescription() {
        return "根据输入数据生成饼状图";
    }
    
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> dataParam = new HashMap<>();
        dataParam.put("type", "string");
        dataParam.put("description", "制表数据;格式为data1;data2;data3;data4,其中data为int类型数据");
        properties.put("data", dataParam);
        
        Map<String, Object> categoriesParam = new HashMap<>();
        categoriesParam.put("type", "string");
        categoriesParam.put("description", "categories for pie chart, categories should be a string contains a list of texts like 'a;b;c;1;2' in order to match the data, each category should be split by ';' ");
        properties.put("categories", categoriesParam);

        Map<String, Object> title = new HashMap<>();
        title.put("type", "string");
        title.put("description", "图表标题");
        properties.put("title", title);
        
        parameters.put("properties", properties);
        parameters.put("required", Lists.newArrayList("data"));
        
        return parameters;
    }
    
    @Override
    public boolean isFinal() {
        return pieProperties.isFinal();
    }
}
