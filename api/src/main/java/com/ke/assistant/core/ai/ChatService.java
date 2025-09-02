package com.ke.assistant.core.ai;

import com.ke.assistant.core.run.ExecutionContext;
import com.ke.bella.openapi.BellaContext;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private OpenAiService openAiService;
    
    /**
     * 流式聊天完成
     */
    public void chat(ExecutionContext context) {
        try {
            BellaContext.replace(context.getBellaContext());
            tryStreamChat(context, 0, 3);
        } finally {
            BellaContext.clearAll();
        }
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

            // 启用流式响应
            request.setStream(true);

            int finalTime = time;
            openAiService.streamChatCompletion(request)
                    .subscribe(context::publish, throwable -> {
                        if(finalTime > maxTimes) {
                            context.setError("llm_error", throwable.getMessage());
                        } else {
                            tryStreamChat(context, finalTime, maxTimes);
                        }
                    }, () -> context.publish("[LLM_DONE]"));

        } catch (Exception e) {
            if(time < maxTimes) {
                tryStreamChat(context, time, maxTimes);
            }
            logger.error("Stream chat completion failed for model: {}", context.getModel(), e);
            context.setError("llm_error", e.getMessage());
        }
    }
}
