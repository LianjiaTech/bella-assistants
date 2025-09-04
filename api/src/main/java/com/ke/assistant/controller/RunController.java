package com.ke.assistant.controller;

import com.ke.assistant.core.run.RunExecutor;
import com.ke.assistant.core.run.RunStateManager;
import com.ke.assistant.model.CommonPage;
import com.ke.assistant.service.RunService;
import com.ke.assistant.service.ThreadService;
import com.ke.assistant.util.MessageUtils;
import com.ke.assistant.util.ToolUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.theokanning.openai.assistants.run.ModifyRunRequest;
import com.theokanning.openai.assistants.run.Run;
import com.theokanning.openai.assistants.run.RunCreateRequest;
import com.theokanning.openai.assistants.run.SubmitToolOutputs;
import com.theokanning.openai.assistants.run.SubmitToolOutputsRequest;
import com.theokanning.openai.assistants.run.ToolCall;
import com.theokanning.openai.assistants.run.ToolCallFunction;
import com.theokanning.openai.assistants.run_step.RunStep;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Run Controller
 */
@RestController
@RequestMapping("/v1/threads/{thread_id}/runs")
@Slf4j
public class RunController {

    @Autowired
    private RunService runService;
    @Autowired
    private ThreadService threadService;
    @Autowired
    private RunExecutor runExecutor;
    @Autowired
    private RunStateManager runStateManager;

    /**
     * 创建 Run
     */
    @PostMapping
    public Object createRun(
            @PathVariable("thread_id") String threadId,
            @RequestBody RunCreateRequest request) {

        ToolUtils.checkTools(request.getTools());

        // 验证thread是否存在
        if(threadService.getThreadById(threadId) == null) {
            throw new ResourceNotFoundException("Thread not found");
        }

        // 创建Run和初始消息
        Pair<Run, String> pair = runService.createRun(threadId, request, MessageUtils.getAttachments(request.getAdditionalMessages()));

        SseEmitter emitter = null;
        // 如果是流式请求，返回SseEmitter
        if(Boolean.TRUE.equals(request.getStream())) {
            emitter = new SseEmitter(300000L); // 5分钟超时
            // 启动流式执行
        }
        runExecutor.startRun(threadId, pair.getLeft().getId(), pair.getRight(), false, emitter, BellaContext.snapshot());
        return Boolean.TRUE.equals(request.getStream()) ? emitter : pair.getLeft();
    }

    /**
     * 提交工具结果
     */
    @PostMapping("/{run_id}/submit_tool_outputs")
    public Object submitToolOutputs(
            @PathVariable("thread_id") String threadId,
            @PathVariable("run_id") String runId,
            @RequestBody SubmitToolOutputsRequest request) {

        // 验证run是否存在且属于指定的thread
        Run existing = runService.getRunById(threadId, runId);
        if(existing == null) {
            throw new ResourceNotFoundException("Run not found");
        }

        if(!threadId.equals(existing.getThreadId())) {
            throw new BizParamCheckException("Run does not belong to this thread");
        }

        Assert.notEmpty(request.getToolOutputs(), "output can not be null");

        // 构造SubmitToolOutputs
        SubmitToolOutputs submitToolOutputs = new SubmitToolOutputs();
        submitToolOutputs.setToolCalls(request.getToolOutputs().stream()
            .map(output -> {
                ToolCall toolCall = new ToolCall();
                toolCall.setId(output.getToolCallId());
                if(output.getOutput() != null) {
                    ToolCallFunction function = new ToolCallFunction();
                    function.setOutput(output.getOutput());
                    toolCall.setFunction(function);
                }
                return toolCall;
            })
            .collect(Collectors.toList()));
        
        // 提交工具输出
        runStateManager.submitRequiredAction(threadId, runId, submitToolOutputs, LocalDateTime.now().plusMinutes(5));

       RunStep runStep = runService.getRunSteps(threadId, runId).stream().filter(r -> "message_creation".equals(r.getType()))
               .findAny().orElseThrow(() -> new IllegalStateException("server_error:no message creation step type"));

        String assistantMessageId  = runStep.getStepDetails().getMessageCreation().getMessageId();

        SseEmitter emitter = null;
        // 如果是流式请求，返回SseEmitter
        if(Boolean.TRUE.equals(request.getStream())) {
            emitter = new SseEmitter(300000L); // 5分钟超时
        }
        runExecutor.resumeRun(threadId, runId, assistantMessageId, emitter, BellaContext.snapshot());
        return Boolean.TRUE.equals(request.getStream()) ? emitter : runService.getRunById(threadId, runId);
    }

    /**
     * 取消 Run
     */
    @PostMapping("/{run_id}/cancel")
    public Run cancelRun(
            @PathVariable("thread_id") String threadId,
            @PathVariable("run_id") String runId) {

        // 验证run是否存在且属于指定的thread
        Run existing = runService.getRunById(threadId, runId);
        if(existing == null) {
            throw new ResourceNotFoundException("Run not found");
        }

        if(!threadId.equals(existing.getThreadId())) {
            throw new BizParamCheckException("Run does not belong to this thread");
        }

        // 取消run
        runStateManager.toCancelling(threadId, runId);
        
        return runService.getRunById(threadId, runId);
    }

    /**
     * 获取 Run 详情
     */
    @GetMapping("/{run_id}")
    public Run getRun(
            @PathVariable("thread_id") String threadId,
            @PathVariable("run_id") String runId) {

        Run run = runService.getRunById(threadId, runId);
        if(run == null) {
            throw new ResourceNotFoundException("Run not found");
        }

        if(!threadId.equals(run.getThreadId())) {
            throw new BizParamCheckException("Message does not belong to this thread");
        }


        // 验证run是否属于指定的thread
        if(!threadId.equals(run.getThreadId())) {
            throw new BizParamCheckException("Run does not belong to this thread");
        }

        return run;
    }

    /**
     * 获取 Thread 的 Run 列表
     */
    @GetMapping
    public CommonPage<Run> listRuns(
            @PathVariable("thread_id") String threadId,
            @RequestParam(value = "after", required = false) String after,
            @RequestParam(value = "before", required = false) String before,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "order", defaultValue = "desc") String order) {

        List<Run> infoList = runService.getRunsByCursor(threadId, after, before, limit + 1, order);

        boolean hasMore = infoList.size() > limit;
        if (hasMore) {
            infoList.remove(infoList.size() - 1);
        }

        String firstId = infoList.isEmpty() ? null : infoList.get(0).getId();
        String lastId = infoList.isEmpty() ? null : infoList.get(infoList.size() - 1).getId();

        return new CommonPage<>(infoList, firstId, lastId, hasMore);
    }

    /**
     * 更新 Run
     */
    @PostMapping("/{run_id}")
    public Run updateRun(
            @PathVariable("thread_id") String threadId,
            @PathVariable("run_id") String runId,
            @RequestBody ModifyRunRequest request) {

        // 验证run是否存在且属于指定的thread
        Run existing = runService.getRunById(threadId, runId);
        if(existing == null) {
            throw new ResourceNotFoundException("Run not found");
        }

        if(!threadId.equals(existing.getThreadId())) {
            throw new BizParamCheckException("Message does not belong to this thread");
        }


        return runService.updateRun(threadId, runId, request.getMetadata());
    }

    /**
     * 获取 Run 的 Steps 列表
     */
    @GetMapping("/{run_id}/steps")
    public List<RunStep> getRunSteps(
            @PathVariable("thread_id") String threadId,
            @PathVariable("run_id") String runId) {

        // 验证run是否存在且属于指定的thread
        Run existing = runService.getRunById(threadId, runId);
        if(existing == null) {
            throw new ResourceNotFoundException("Run not found");
        }

        if(!threadId.equals(existing.getThreadId())) {
            throw new BizParamCheckException("Message does not belong to this thread");
        }


        return runService.getRunSteps(threadId, runId);
    }

}
