package com.ke.assistant.core.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ke.assistant.core.run.ExecutionContext;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.service.OpenAiService;

@Component
public class ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private OpenAiServiceFactory openAiServiceFactory;
    
    /**
     * 流式聊天完成
     */
    public void chat(ExecutionContext context) {
        tryStreamChat(openAiServiceFactory.create(), context,0, 3);
    }


    private void tryStreamChat(OpenAiService aiService, ExecutionContext context, int time, int maxTimes) {
        ++time;
        try {
            logger.debug("Starting stream chat completion with model: {}", context.getModel());

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(context.getModel())
                    .messages(context.getChatMessages())
                    .build();
            if(context.getChatTools() != null && !context.getChatTools().isEmpty()) {
                request.setTools(context.getChatTools());
                request.setToolChoice(context.getToolChoice());
            }

            // 添加支持的参数
            if(context.isSupportTemperature()) {
                request.setTemperature(context.getTemperature());
            }

            if(context.isSupportTopP()) {
                request.setTopP(context.getTopP());
            }

            if(context.isSupportMaxTokens()) {
                request.setMaxTokens(context.getMaxCompletionTokens());
            }

            // 开启深度思考，通常深度思考模型不支持温度参数
            if(context.isReasoningMode()) {
                request.setReasoningEffort(context.getRun().getReasoningEffort());
                request.setTemperature(null);
            }

            // 启用流式响应
            request.setStream(true);

            int finalTime = time;
            aiService.streamChatCompletion(request)
                    .subscribe(context::publish, throwable -> {
                        logger.warn(throwable.getMessage(), throwable);
                        String code = "llm_error";
                        String message = throwable.getMessage();
                        boolean retry = true;
                        if(throwable instanceof OpenAiHttpException httpException) {
                            code = httpException.type;
                            message = httpException.getMessage();
                            retry = httpException.statusCode == 499 ||
                                    httpException.statusCode == 429 ||
                                    (httpException.statusCode > 500 && httpException.statusCode != 503);
                        }
                        if(!retry || finalTime > maxTimes) {
                            context.setError(code, message);
                        } else {
                            tryStreamChat(aiService, context, finalTime, maxTimes);
                        }
                    }, () -> context.publish("[LLM_DONE]"));

        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            if(time < maxTimes) {
                tryStreamChat(aiService, context, time, maxTimes);
                return;
            }
            logger.error("Stream chat completion failed for model: {}", context.getModel(), e);
            context.setError("server_error", e.getMessage());
        }
    }
}
