package com.ke.assistant.core.ai;

import com.ke.assistant.core.run.ExecutionContext;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private OpenAiServiceFactory openAiServiceFactory;
    
    /**
     * 流式聊天完成
     */
    public void chat(ExecutionContext context) {
        tryStreamChat(context, 0, 3);
    }


    private void tryStreamChat(ExecutionContext context, int time, int maxTimes) {
        ++time;
        try {
            logger.debug("Starting stream chat completion with model: {}", context.getModel());

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(context.getModel())
                    .messages(context.getChatMessages())
                    .maxTokens(context.getMaxCompletionTokens())
                    .topP(context.getTopP())
                    .temperature(context.getTemperature())
                    .build();
            if(context.getChatTools() != null) {
                request.setTools(context.getChatTools());
                request.setToolChoice(context.getToolChoice());
            }
            // 开启深度思考，通常深度思考模型不支持温度参数
            if(context.getModelFeatures().isReason_content()) {
                request.setReasoningEffort("medium");
                request.setTemperature(null);
            }

            // 启用流式响应
            request.setStream(true);

            int finalTime = time;
            openAiServiceFactory.create().streamChatCompletion(request)
                    .subscribe(context::publish, throwable -> {
                        logger.warn(throwable.getMessage(), throwable);
                        if(finalTime > maxTimes) {
                            context.setError("llm_error", throwable.getMessage());
                        } else {
                            try {
                                BellaContext.replace(context.getBellaContext());
                                tryStreamChat(context, finalTime, maxTimes);
                            } finally {
                                BellaContext.clearAll();
                            }
                        }
                    }, () -> context.publish("[LLM_DONE]"));

        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            if(time < maxTimes) {
                tryStreamChat(context, time, maxTimes);
                return;
            }
            logger.error("Stream chat completion failed for model: {}", context.getModel(), e);
            context.setError("llm_error", e.getMessage());
        }
    }
}
