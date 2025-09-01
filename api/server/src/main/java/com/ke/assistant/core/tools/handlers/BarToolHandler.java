package com.ke.assistant.core.tools.handlers;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 柱状图生成工具处理器
 */
@Slf4j
@Component
public class BarToolHandler implements ToolHandler {
    
    @Autowired
    private AssistantProperties assistantProperties;
    
    @Autowired
    private ChartService chartService;
    
    @Autowired
    private S3Service s3Service;
    
    private ToolProperties.BarToolProperties barProperties;
    
    @PostConstruct
    public void init() {
        this.barProperties = assistantProperties.getTools().getBarTool();
    }
    
    @Override
    public ToolResult execute(ToolContext context, JsonNode arguments, ToolOutputChannel channel) {
        
        // 解析参数
        String data = arguments.get("data").asText();
        if (data == null || data.trim().isEmpty()) {
            // 返回错误消息
            Map<String, Object> outputData = new HashMap<>();
            outputData.put("type", "text");
            outputData.put("message", "please input data");
            Map<String, String> metas = new HashMap<>();
            outputData.put("meta", metas);
            
            return ToolResult.builder().output(JacksonUtils.serialize(outputData)).build();
        }
        
        JsonNode xAxisNode = arguments.get("x_axis");
        String xAxis = xAxisNode != null ? xAxisNode.asText() : null;
        
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
        
        // 解析X轴标签
        String[] xAxisLabels = null;
        if (xAxis != null && !xAxis.trim().isEmpty()) {
            xAxisLabels = xAxis.split(";");
            if (xAxisLabels.length != values.length) {
                xAxisLabels = null; // 长度不匹配时忽略标签
            }
        }
        
        // 检查S3服务是否配置
        if (!s3Service.isConfigured()) {
            return ToolResult.builder().output(JacksonUtils.serialize("S3存储服务未配置，无法生成柱状图。")).build();
        }

        JsonNode titleNode = arguments.get("title");
        String title = titleNode == null ? "柱状图" : titleNode.asText();

        JsonNode xTagNode = arguments.get("xAxisTag");
        String xTag = xTagNode == null ? "类别" : xTagNode.asText();

        JsonNode yTagNode = arguments.get("yAxisTag");
        String yTag = xTagNode == null ? "数值" : yTagNode.asText();

        String imageUrl;
        try {
            // 使用JFreeChart生成柱状图
            byte[] chartBytes = chartService.generateBarChart(
                title,                    // 图表标题
                xTag,                     // X轴标签
                yTag,                     // Y轴标签
                values,                     // 数据数组
                xAxisLabels,                // X轴标签数组
                barProperties.getWidth(),   // 宽度
                barProperties.getHeight()   // 高度
            );
            
            // 上传到S3（必需）
            imageUrl = s3Service.uploadChart(chartBytes, "jpg");
            log.info("Generated bar chart with {} data points and uploaded to S3: {}", values.length, imageUrl);
            
        } catch (Exception e) {
            log.error("Failed to generate bar chart or upload to S3", e);
            
            // 返回错误消息
            return ToolResult.builder().output("生成柱状图或上传到S3失败: " + e.getMessage()).build();
        }
        
        // 构建返回结果
        ImageUrl output = new ImageUrl();
        output.setUrl(imageUrl);

        if(isFinal()) {
            channel.output(context.getToolId(), output);
        }
        
        return ToolResult.builder().output(imageUrl).build();
    }
    
    @Override
    public String getToolName() {
        return "generate_bar";
    }
    
    @Override
    public String getDescription() {
        return "根据数据生成柱状图";
    }
    
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> dataParam = new HashMap<>();
        dataParam.put("type", "string");
        dataParam.put("description", "分号分隔的数值数据");
        properties.put("data", dataParam);
        
        Map<String, Object> xAxisParam = new HashMap<>();
        xAxisParam.put("type", "string");
        xAxisParam.put("description", "分号分隔的X轴标签（可选）");
        properties.put("x_axis", xAxisParam);

        Map<String, Object> xAxisTag = new HashMap<>();
        xAxisParam.put("type", "string");
        xAxisParam.put("description", "x轴的类别标签（可选）");
        properties.put("xAxisTag", xAxisTag);

        Map<String, Object> yAxisTag = new HashMap<>();
        xAxisParam.put("type", "string");
        xAxisParam.put("description", "y轴的标签（可选）");
        properties.put("yAxisTag", yAxisTag);

        Map<String, Object> title = new HashMap<>();
        xAxisParam.put("type", "string");
        xAxisParam.put("description", "图表标题");
        properties.put("title", title);
        
        parameters.put("properties", properties);
        parameters.put("required", Lists.newArrayList("data"));
        
        return parameters;
    }
    
    @Override
    public boolean isFinal() {
        return barProperties.isFinal();
    }
}
