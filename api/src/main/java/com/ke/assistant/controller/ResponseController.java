package com.ke.assistant.controller;

import com.ke.assistant.core.run.ExecutionContext;
import com.ke.assistant.core.run.RunExecutor;
import com.ke.assistant.db.context.RepoContext;
import com.ke.assistant.model.ResponseCreateResult;
import com.ke.assistant.service.ResponseService;
import com.ke.bella.openapi.BellaContext;
import com.theokanning.openai.response.CreateResponseRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Response API Controller
 * Implements OpenAI Response API endpoints
 */
@RestController
@RequestMapping("/v1/responses")
@Slf4j
public class ResponseController {

    @Autowired
    private ResponseService responseService;
    
    @Autowired
    private RunExecutor runExecutor;

    /**
     * Create a new response
     * POST /v1/responses
     */
    @PostMapping
    public Object createResponses(@RequestBody CreateResponseRequest request) throws ExecutionException, InterruptedException, TimeoutException {
        log.info("Creating response with model: {}, stream: {}", request.getModel(), request.getStream());

        request.setUser(BellaContext.getOwnerCode());

        boolean nonStore = Boolean.FALSE.equals(request.getStore());
        if (nonStore) {
            RepoContext.activate();
        }

        try {
            // Create response and prepare for execution
            ResponseCreateResult result = responseService.createResponse(request);

            SseEmitter emitter = null;
            // If streaming is requested, return SseEmitter
            if (Boolean.TRUE.equals(request.getStream())) {
                emitter = new SseEmitter(600000L); // 10 minute timeout
            }

            // Start response execution
            ExecutionContext context = runExecutor.startResponseRun(
                result.getThreadId(),
                result.getRunId(),
                result.getAssistantMessageId(),
                result.getAdditionalMessages(),
                result.isNewThread(),
                result.getResponse(),
                emitter,
                BellaContext.snapshot()
            );

            return Boolean.TRUE.equals(request.getStream()) ? emitter :
                    Boolean.TRUE.equals(request.getBackground()) ? result.getResponse() : context.blockingGetResult(600);
        } finally {
            if (nonStore) {
                RepoContext.detach();
            }
        }
    }

    /**
     * get responses execution result
     */
    @GetMapping("/{response_id}")
    public Object getResponses(@PathVariable("response_id") String responseId,
            @RequestParam(required = false) boolean stream,
            @RequestParam(value = "starting_after", required = false) Integer startingAfter) {
        if(stream) {
            throw new NotImplementedException("Can not support stream mode");
        }
        return responseService.getResponse(responseId);
    }
    
}
