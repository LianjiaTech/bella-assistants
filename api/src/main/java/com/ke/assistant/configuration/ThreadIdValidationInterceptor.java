package com.ke.assistant.configuration;

import com.ke.assistant.service.ConversationService;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Interceptor to validate that Assistant API endpoints are not invoked
 * with a threadId that belongs to Response API.
 *
 * Rule: If the incoming request path contains a `thread_id` path variable,
 * we verify that it does not exist in `ResponseIdMapping`. If it does, the
 * request is rejected with a business parameter exception.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ThreadIdValidationInterceptor implements HandlerInterceptor {

    private final ConversationService conversationService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> uriVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (uriVariables == null || uriVariables.isEmpty()) {
            return true;
        }

        // Validate any path variable that represents a thread id
        for (Map.Entry<String, String> entry : uriVariables.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null && key.toLowerCase().contains("thread_id") && StringUtils.isNotBlank(value)) {
                boolean isConversation = conversationService.isConversation(value);
                if (isConversation) {
                    log.warn("Rejecting Assistant API request with Response API conversation [{}]: {}", key, value);
                    throw new BizParamCheckException("This conversation belongs to Response API and is not allowed for Assistant API endpoints");
                }
            }
        }

        return true;
    }
}
