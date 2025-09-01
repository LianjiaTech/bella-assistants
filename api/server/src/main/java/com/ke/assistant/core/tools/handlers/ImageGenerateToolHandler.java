package com.ke.assistant.core.tools.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.configuration.ToolProperties;
import com.ke.assistant.core.tools.BellaToolHandler;
import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import com.ke.assistant.service.S3Service;
import com.theokanning.openai.image.CreateImageRequest;
import com.theokanning.openai.image.Image;
import com.theokanning.openai.image.ImageResult;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 图片生成工具处理器
 */
@Slf4j
@Component
public class ImageGenerateToolHandler implements BellaToolHandler {

    @Autowired
    private OpenAiService openAiService;
    
    @Autowired
    private AssistantProperties assistantProperties;
    
    @Autowired
    private S3Service s3Service;
    
    private ToolProperties.ImageGenerateToolProperties imageProperties;
    
    @PostConstruct
    public void init() {
        this.imageProperties = assistantProperties.getTools().getImageGenerate();
    }
    
    @Override
    public ToolResult doExecute(ToolContext context, JsonNode arguments, ToolOutputChannel channel) {
        // 检查S3服务是否配置
        if (!s3Service.isConfigured()) {
            return ToolResult.builder().output("S3存储服务未配置，无法生成图片。").build();
        }
        
        try {
            // 解析参数
            String prompt = arguments.get("prompt").asText();
            if (prompt == null || prompt.trim().isEmpty()) {
                throw new IllegalArgumentException("prompt参数不能为空");
            }
            
            // 获取可选参数
            String size = "1024x1024";  // 默认尺寸
            if (arguments.has("size") && !arguments.get("size").isNull()) {
                size = arguments.get("size").asText();
            }
            
            String quality = "standard";  // 默认质量
            if (arguments.has("quality") && !arguments.get("quality").isNull()) {
                quality = arguments.get("quality").asText();
            }
            
            String style = "vivid";  // 默认风格
            if (arguments.has("style") && !arguments.get("style").isNull()) {
                style = arguments.get("style").asText();
            }
            
            String model = "dall-e-3";
            String responseFormat = "b64_json";

            
            log.info("开始生成图片: prompt={}, size={}, quality={}, style={}, model={}", 
                    prompt, size, quality, style, model);
            
            // 调用OpenAI图像生成API
            String result = generateImage(prompt, size, quality, style, model, responseFormat);
            
            if(isFinal()) {
                channel.output(context.getToolId(), result);
            }
            
            log.info("图片生成完成");
            return ToolResult.builder().output(result).build();
            
        } catch (Exception e) {
            log.error("图片生成或上传到S3失败", e);
            String errorMsg = "图片生成或上传到S3失败: " + e.getMessage();
            
            if(isFinal()) {
                channel.output(context.getToolId(), errorMsg);
            }
            
            return ToolResult.builder().output(errorMsg).build();
        }
    }
    
    /**
     * 使用OpenAI API生成图片
     */
    private String generateImage(String prompt, String size, String quality, String style, String model, String responseFormat) {
        try {
            // 构建图片生成请求
            CreateImageRequest.CreateImageRequestBuilder requestBuilder = CreateImageRequest.builder()
                    .prompt(prompt)
                    .size(size)
                    .quality(quality)
                    .style(style)
                    .model(model)
                    .responseFormat(responseFormat)
                    .n(1);  // 生成1张图片
            
            CreateImageRequest request = requestBuilder.build();
            
            // 调用API
            ImageResult result = openAiService.createImage(request);
            
            if (result.getData() != null && !result.getData().isEmpty()) {
                Image image = result.getData().get(0);
                String base64Data = image.getB64Json();
                
                // 将Base64数据转换为字节数组
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                
                // 上传到S3并返回URL
                String s3Url = s3Service.uploadChart(imageBytes, "png");
                log.info("Generated image uploaded to S3: {}", s3Url);
                
                return s3Url;
            } else {
                throw new RuntimeException("OpenAI API返回空的图片数据");
            }
            
        } catch (Exception e) {
            log.error("调用OpenAI图像生成API失败", e);
            throw new RuntimeException("调用OpenAI图像生成API失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getToolName() {
        return "img_generate";
    }
    
    @Override
    public String getDescription() {
        return "根据文本描述生成图片";
    }
    
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        // prompt 参数 (必需)
        Map<String, Object> promptParam = new HashMap<>();
        promptParam.put("type", "string");
        promptParam.put("description", "图片生成的文本描述");
        properties.put("prompt", promptParam);
        
        // size 参数 (可选)
        Map<String, Object> sizeParam = new HashMap<>();
        sizeParam.put("type", "string");
        sizeParam.put("description", "生成图片的尺寸，DALL-E 3支持: 1024x1024, 1792x1024, 1024x1792");
        sizeParam.put("enum", Lists.newArrayList("1024x1024", "1792x1024", "1024x1792"));
        properties.put("size", sizeParam);
        
        // quality 参数 (可选)
        Map<String, Object> qualityParam = new HashMap<>();
        qualityParam.put("type", "string");
        qualityParam.put("description", "图片质量，standard为标准质量，hd为高清");
        qualityParam.put("enum", Lists.newArrayList("standard", "hd"));
        properties.put("quality", qualityParam);
        
        // style 参数 (可选)
        Map<String, Object> styleParam = new HashMap<>();
        styleParam.put("type", "string");
        styleParam.put("description", "图片风格，vivid生成更生动戏剧化的图片，natural生成更自然的图片");
        styleParam.put("enum", Lists.newArrayList("vivid", "natural"));
        properties.put("style", styleParam);
        
        parameters.put("properties", properties);
        parameters.put("required", Lists.newArrayList("prompt"));
        
        return parameters;
    }
    
    @Override
    public boolean isFinal() {
        return imageProperties.isFinal();
    }
}
