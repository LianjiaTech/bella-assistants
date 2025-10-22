package com.ke.assistant.core.tools.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import com.ke.assistant.core.tools.ToolHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.configuration.ToolProperties;
import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.UserMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * 图片视觉理解工具处理器
 */
@Slf4j
@Component
public class ImgVisionToolHandler implements ToolHandler {

    @Autowired
    private OpenAiServiceFactory openAiServiceFactory;

    @Autowired
    private AssistantProperties assistantProperties;

    private ToolProperties.ImgVisionToolProperties visionToolProperties;

    @PostConstruct
    public void init() {
        this.visionToolProperties = assistantProperties.getTools().getImgVision();
    }
    
    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {

        String model = visionToolProperties.getModel();
        if(model == null || model.isEmpty()) {
            return new ToolResult("模型未配置，无法生成图片。");
        }

        try {
            // 解析参数
            String imageUrl = Optional.ofNullable(arguments.get("image_url")).map(Object::toString).orElse(null);

            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("image_url参数不能为空");
            }
            
            // 获取可选的提示词参数
            String prompt = Optional.ofNullable(arguments.get("prompt")).map(Object::toString)
                    .orElse("请分析这张图片，描述你看到的内容，包括物体、场景、文字等详细信息。");
            
            // 获取可选的detail参数
            String detail = Optional.ofNullable(arguments.get("detail")).map(Object::toString).orElse("auto");

            log.info("开始分析图片: {}, 提示词: {}, 详细度: {}", imageUrl, prompt, detail);
            
            // 调用OpenAI Vision API
            String analysisResult = analyzeImage(imageUrl, prompt, detail, model);
            
            if(isFinal()) {
                channel.output(context.getToolId(), analysisResult);
            }
            
            log.info("图片分析完成，结果长度: {}", analysisResult.length());
            return new ToolResult(ToolResult.ToolResultType.text, analysisResult);
            
        } catch (Exception e) {
            log.error("图片分析失败", e);
            String errorMsg = "图片分析失败: " + e.getMessage();
            
            if(isFinal()) {
                channel.output(context.getToolId(), errorMsg);
            }
            
            return new ToolResult(errorMsg);
        }
    }
    
    /**
     * 使用OpenAI Vision API分析图片
     */
    private String analyzeImage(String imageUrl, String prompt, String detail, String model) {
        try {
            // 构建消息
            List<ChatMessage> messages = new ArrayList<>();
            
            // 创建包含文本和图片的用户消息
            UserMessage userMessage = UserMessage.builder()
                    .buildImageMessageWithDetail(prompt, detail, imageUrl);
            
            messages.add(userMessage);
            
            // 构建请求
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .maxTokens(1000)
                    .temperature(0.7)
                    .build();
            
            // 调用API
            ChatCompletionResult result = openAiServiceFactory.create().createChatCompletion(request);
            
            if (result.getChoices() != null && !result.getChoices().isEmpty()) {
                return result.getChoices().get(0).getMessage().getContent();
            } else {
                throw new RuntimeException("OpenAI API返回空结果");
            }
            
        } catch (Exception e) {
            log.error("调用OpenAI Vision API失败", e);
            throw new RuntimeException("调用OpenAI Vision API失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getToolName() {
        return "img_vision";
    }
    
    @Override
    public String getDescription() {
        return "这个工具可以用来解读图像并从中提取信息，输入应该是需要处理的图像的URL和图像处理工具的使用说明。";
    }
    
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        // image_url 参数 (必需)
        Map<String, Object> imageUrlParam = new HashMap<>();
        imageUrlParam.put("type", "string");
        imageUrlParam.put("description", "要分析的图片URL或base64数据");
        properties.put("image_url", imageUrlParam);
        
        // prompt 参数 (可选)
        Map<String, Object> promptParam = new HashMap<>();
        promptParam.put("type", "string");
        promptParam.put("description", "根据用户问题，为大模型提供分析图片时的提示词，不提供时使用默认提示词");
        properties.put("prompt", promptParam);
        
        // detail 参数 (可选)
        Map<String, Object> detailParam = new HashMap<>();
        detailParam.put("type", "string");
        detailParam.put("description", "图片分析的详细程度，可选值: auto, low, high");
        detailParam.put("enum", Lists.newArrayList("auto", "low", "high"));
        properties.put("detail", detailParam);
        
        parameters.put("properties", properties);
        parameters.put("required", Lists.newArrayList("image_url"));
        
        return parameters;
    }
    
    @Override
    public boolean isFinal() {
        return visionToolProperties.isFinal();
    }
}
