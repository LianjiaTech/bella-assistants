package com.ke.assistant.core.tools.handlers;

import com.google.common.collect.Lists;
import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.configuration.ToolProperties;
import com.ke.assistant.core.tools.BellaToolHandler;
import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import com.ke.assistant.core.tools.ToolStreamEvent;
import com.ke.assistant.db.context.RepoContext;
import com.ke.assistant.service.S3Service;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.completion.chat.ImageUrl;
import com.theokanning.openai.image.CreateImageRequest;
import com.theokanning.openai.image.Image;
import com.theokanning.openai.image.ImageResult;
import com.theokanning.openai.response.ItemStatus;
import com.theokanning.openai.response.stream.ImageGenerationCompletedEvent;
import com.theokanning.openai.response.stream.ImageGenerationGeneratingEvent;
import com.theokanning.openai.response.stream.ImageGenerationInProgressEvent;
import com.theokanning.openai.response.stream.ImageGenerationPartialImageEvent;
import com.theokanning.openai.response.tool.ImageGenerationToolCall;
import com.theokanning.openai.response.tool.definition.ImageGenerationTool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 图片生成工具处理器
 */
@Slf4j
@Component
public class ImageGenerateToolHandler extends BellaToolHandler {

    @Autowired
    private OpenAiServiceFactory openAiServiceFactory;
    
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
    public ToolResult doExecute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        ItemStatus status = ItemStatus.INCOMPLETE;
        ImageGenerationToolCall toolCall = new ImageGenerationToolCall();
        boolean noStore = RepoContext.isActive();
        toolCall.setDataType(noStore ? "b64_json" : "url");
        try {
            channel.output(context.getToolId(), context.getTool(), ToolStreamEvent.builder().toolCallId(context.getToolId())
                    .executionStage(ToolStreamEvent.ExecutionStage.prepare)
                    .event(ImageGenerationInProgressEvent.builder().build())
                    .build());
            // 检查S3服务是否配置
            if(!s3Service.isConfigured() && !noStore) {
                return new ToolResult("S3存储服务未配置，无法生成图片。");
            }

            Tool.ImgGenerate imgGenerate = (Tool.ImgGenerate) context.getTool();
            ImageGenerationTool definition = imgGenerate.getDefinition();

            String model = definition == null || definition.getModel() == null ? imageProperties.getModel() : definition.getModel();
            if(model == null || model.isEmpty()) {
                return new ToolResult("模型未配置，无法生成图片。");
            }

            // 解析参数
            String prompt = Optional.ofNullable(arguments.get("prompt")).map(Object::toString).orElse(null);
            if(prompt == null || prompt.trim().isEmpty()) {
                throw new IllegalArgumentException("prompt参数不能为空");
            }

            // 获取可选参数
            String size = Optional.ofNullable(arguments.get("size")).map(Object::toString).orElse("1024x1024");
            String quality = Optional.ofNullable(arguments.get("quality")).map(Object::toString).orElse("standard");
            String style = Optional.ofNullable(arguments.get("style")).map(Object::toString).orElse("vivid");

            String responseFormat = "b64_json";

            log.info("开始生成图片: prompt={}, size={}, quality={}, style={}, model={}",
                    prompt, size, quality, style, model);

            channel.output(context.getToolId(), context.getTool(), ToolStreamEvent.builder().toolCallId(context.getToolId())
                    .executionStage(ToolStreamEvent.ExecutionStage.processing)
                    .event(ImageGenerationGeneratingEvent.builder().build())
                    .build());

            // 调用OpenAI图像生成API，返回 (图片url, base64String)
            Pair<String, String> result = generateImage(prompt, size, quality, style, model, responseFormat, definition, noStore);

            channel.output(context.getToolId(), context.getTool(), ToolStreamEvent.builder().toolCallId(context.getToolId())
                    .executionStage(ToolStreamEvent.ExecutionStage.processing)
                    .event(ImageGenerationPartialImageEvent.builder()
                            .partialImageIndex(1)
                            .partialImageUrl(noStore ? result.getRight() : result.getLeft()).build())
                    .build());

            toolCall.setResult(noStore ? result.getRight() : result.getLeft());

            ImageUrl output = new ImageUrl();
            output.setUrl(noStore ? result.getRight() : result.getLeft());

            if(isFinal()) {
                channel.output(context.getToolId(), output);
            }

            log.info("图片生成完成");
            status = ItemStatus.COMPLETED;
            return new ToolResult(ToolResult.ToolResultType.image_url, noStore ? "Generate the required Image Successfully" : result.getLeft());
        } catch (Exception e) {
            log.error("图片生成或上传到S3失败", e);
            String errorMsg = "图片生成或上传到S3失败: " + e.getMessage();

            if(isFinal()) {
                channel.output(context.getToolId(), errorMsg);
            }

            return new ToolResult(errorMsg);
        } finally {
            toolCall.setStatus(status);
            channel.output(context.getToolId(), context.getTool(), ToolStreamEvent.builder().toolCallId(context.getToolId())
                    .executionStage(ToolStreamEvent.ExecutionStage.completed)
                    .event(ImageGenerationCompletedEvent.builder().build())
                    .result(toolCall)
                    .build());
        }
    }
    
    /**
     * 使用OpenAI API生成图片
     */
    private Pair<String, String> generateImage(String prompt, String size, String quality, String style, String model, String responseFormat, ImageGenerationTool definition, boolean noStore) {
        try {
            CreateImageRequest.CreateImageRequestBuilder requestBuilder = CreateImageRequest.builder();

            if(definition != null) {
                if(definition.getSize() != null) {
                    size = definition.getSize();
                }
                if(definition.getQuality() != null) {
                    quality = definition.getQuality();
                }

                if(definition.getOutputFormat() != null) {
                    requestBuilder.outputFormat(definition.getOutputFormat());
                }

                if(definition.getBackground() != null) {
                    requestBuilder.background(definition.getBackground());
                }
            }
            // 构建图片生成请求
            requestBuilder.prompt(prompt)
                    .size(size)
                    .quality(quality)
                    .style(style)
                    .model(model)
                    .responseFormat(responseFormat)
                    .n(1);  // 生成1张图片

            // 需要去除水印
            if(imageProperties.isProcessWatermark()) {
                requestBuilder.watermark(false);
            }

            CreateImageRequest request = requestBuilder.build();
            
            // 调用API
            ImageResult result = openAiServiceFactory.create().createImage(request);
            
            if (result.getData() != null && !result.getData().isEmpty()) {
                Image image = result.getData().get(0);
                String base64Data = image.getB64Json();
                
                // 将Base64数据转换为字节数组
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);

                String s3Url = null;
                if(!noStore) {
                    // 上传到S3并返回URL
                    s3Url = s3Service.uploadChart(imageBytes, "png");
                    log.info("Generated image uploaded to S3: {}", s3Url);
                }
                
                return Pair.of(s3Url, base64Data);
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
        return "This is a tool used to generate images from text";
    }
    
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        // prompt 参数 (必需)
        Map<String, Object> promptParam = new HashMap<>();
        promptParam.put("type", "string");
        promptParam.put("description", "Image prompt of DallE 3, you should describe the image you want to generate as a list of words as possible as detailed");
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
        qualityParam.put("description", "图片质量，standard为标准质量，hd为高清，如无特殊要求，默认选择标准质量");
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
