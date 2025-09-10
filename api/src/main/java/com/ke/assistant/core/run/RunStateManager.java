package com.ke.assistant.core.run;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.ke.assistant.db.generated.tables.pojos.RunDb;
import com.ke.assistant.db.generated.tables.pojos.RunStepDb;
import com.ke.assistant.db.repo.RunRepo;
import com.ke.assistant.db.repo.RunStepRepo;
import com.ke.assistant.mesh.Event;
import com.ke.assistant.mesh.EventConstants;
import com.ke.assistant.mesh.ServiceMesh;
import com.ke.assistant.service.MessageService;
import com.ke.assistant.util.MessageUtils;
import com.ke.assistant.util.RunUtils;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.Usage;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.message.MessageContent;
import com.theokanning.openai.assistants.run.RequiredAction;
import com.theokanning.openai.assistants.run.SubmitToolOutputs;
import com.theokanning.openai.assistants.run.ToolCall;
import com.theokanning.openai.assistants.run_step.RunStep;
import com.theokanning.openai.assistants.run_step.StepDetails;
import com.theokanning.openai.common.LastError;
import com.theokanning.openai.completion.chat.ChatToolCall;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Run状态管理器
 */
@Component
public class RunStateManager {
    
    private static final Logger logger = LoggerFactory.getLogger(RunStateManager.class);
    
    @Autowired
    private RunRepo runRepo;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ServiceMesh serviceMesh;

    @Autowired
    private RunStepRepo runStepRepo;

    private final Cache<String, ExecutionContext> processingCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    @PostConstruct
    public void init() {
        serviceMesh.registerListener(EventConstants.EVENT_CANCEL_RUN, this::cancel);
    }
    
    /**
     * 更新Run状态
     * 
     * @param runId 运行ID
     * @param newStatus 新状态
     * @param lastError 错误信息（可选）
     * @return 是否更新成功
     */
    @Transactional
    public boolean updateRun(String threadId, String runId, RunStatus newStatus, LastError lastError, Usage usage) {
        try {
            RunDb run = runRepo.findByIdForUpdate(threadId, runId);
            if (run == null) {
                logger.error("Run not found: {}", runId);
                return false;
            }
            
            RunStatus currentStatus = RunStatus.fromValue(run.getStatus());
            
            // 检查状态转换是否合法
            if (!currentStatus.canTransitionTo(newStatus)) {
                logger.warn("Invalid status transition for run {}: {} -> {}", 
                    runId, currentStatus, newStatus);
                return false;
            }
            
            // 更新状态
            run.setStatus(newStatus.getValue());
            if(lastError != null) {
                run.setLastError(JacksonUtils.serialize(lastError));
            }
            run.setUpdatedAt(LocalDateTime.now());
            
            // 如果是终止状态，设置完成时间
            if (newStatus.isTerminal()) {
                run.setCompletedAt(LocalDateTime.now());
            }

            if(newStatus.isCanceled()) {
                run.setCancelledAt(LocalDateTime.now());
            }

            if(usage != null) {
                run.setUsage(JacksonUtils.serialize(usage));
            }
            
            runRepo.update(run);
            
            logger.info("Run {} status updated: {} -> {}", runId, currentStatus, newStatus);

            if(newStatus.isStopExecution()) {
                processingCache.invalidate(runId);
                serviceMesh.removeRunningRun(runId);
            }

            return true;
            
        } catch (Exception e) {
            logger.error("Failed to update run status for {}", runId, e);
            return false;
        }
    }

    /**
     * 更新Run状态
     */
    @Transactional
    public boolean updateRunStatus(String threadId, String runId, RunStatus newStatus) {
        return updateRun(threadId, runId, newStatus, null, null);
    }

    /**
     * 更新Run状态
     */
    @Transactional
    public boolean updateRunStatus(ExecutionContext context, RunStatus newStatus, LastError lastError) {
        boolean success = updateRun(context.getThreadId(), context.getRunId(), newStatus, lastError, context.getUsage());
        context.getRun().setStatus(newStatus.getValue());
        if(newStatus.getRunStreamEvent() != null) {
            context.publish(context.getRun());
        }
        return success;
    }
    
    /**
     * 更新Run状态（无错误信息）
     */
    @Transactional
    public boolean updateRunStatus(ExecutionContext context, RunStatus newStatus) {
        return updateRunStatus(context, newStatus, null);
    }


    /**
     * 将Run状态转换为执行中
     */
    @Transactional
    public boolean toInProgress(ExecutionContext context) {

        Message message = messageService.getMessageById(context.getThreadId(), context.getAssistantMessageId());

        RunStep runStep = context.getCurrentRunStep();

        if(!RunStatus.IN_PROGRESS.getValue().equals(message.getStatus())) {
            throw new IllegalStateException("invalid message status");
        }

        if(!RunStatus.IN_PROGRESS.getValue().equals(runStep.getStatus())) {
            throw new IllegalStateException("invalid run step");
        }

        boolean success = updateRunStatus(context.getThreadId(), context.getRunId(), RunStatus.IN_PROGRESS);
        if(success) {
            context.getRun().setStatus(RunStatus.IN_PROGRESS.getValue());
            context.publish(context.getRun());
            context.publish(runStep);
            context.publish(message);
            serviceMesh.addRunningRun(context.getRunId(), (int) (DateTimeUtils.getCurrentSeconds() + context.getExecutionSeconds()));
            processingCache.put(context.getRunId(), context);
        }
        return success;
    }

    /**
     * 将Run Step状态转换为需要操作
     */
    @Transactional
    public boolean toRequiresAction(ExecutionContext context) {
        runRepo.updateRequireAction(context.getThreadId(), context.getRunId(), JacksonUtils.serialize(context.getRequiredAction().get()));
        context.getRun().setRequiredAction(context.getRequiredAction().get());
        updateRunStatus(context, RunStatus.REQUIRES_ACTION);
        return true;
    }
    
    /**
     * 将Run状态转换为完成
     */
    @Transactional
    public boolean toCompleted(ExecutionContext context) {
        context.setCompleted(true);
        return updateRunStatus(context, RunStatus.COMPLETED);
    }

    
    /**
     * 将Run状态转换为失败
     */
    @Transactional
    public boolean toFailed(ExecutionContext context) {
        LastError lastError = context.getLastError();
        if(context.getCurrentToolCallStepId() != null) {
            updateRunStepStatus(context.getCurrentToolCallStepId(), RunStatus.FAILED, lastError, context);
        }
        updateRunStepStatus(context.getCurrentRunStep().getId(), RunStatus.FAILED, lastError, context);
        boolean success = updateRunStatus(context, RunStatus.FAILED, lastError);
        if(success) {
            context.publish(context.getLastError());
        }
        return success;
    }

    /**
     * 将Run状态转换为超时
     */
    @Transactional
    public boolean toExpired(ExecutionContext context) {
        if(context.getCurrentToolCallStepId() != null) {
            updateRunStepStatus(context.getCurrentToolCallStepId(), RunStatus.EXPIRED, null, context);
        }
        updateRunStepStatus(context.getCurrentRunStep().getId(), RunStatus.EXPIRED, null, context);
        return updateRunStatus(context, RunStatus.EXPIRED, context.getLastError());
    }
    
    /**
     * 将Run状态转换为取消中
     */
    @Transactional
    public boolean toCancelling(String threadId, String runId) {
        boolean success = updateRunStatus(threadId, runId, RunStatus.CANCELLING);
        if(success) {
            String instantId = serviceMesh.getRunningRunInstanceId(runId);
            Event event = Event.cancelEvent(runId);
            if(serviceMesh.getInstanceId().equals(instantId)) {
                cancel(event);
            }
            if(instantId != null) {
                serviceMesh.sendPrivateMessage(instantId, event);
            } else {
                serviceMesh.sendBroadcastMessage(event);
            }
        }
        return success;
    }

    /**
     * 将Run状态转换为取消
     */
    @Transactional
    public boolean toCanceled(ExecutionContext context) {
        if(context.getCurrentToolCallStepId() != null) {
            updateRunStepStatus(context.getCurrentToolCallStepId(), RunStatus.CANCELLED, null, context);
        }
        updateRunStepStatus(context.getCurrentRunStep().getId(), RunStatus.CANCELLED, null, context);
        return updateRunStatus(context, RunStatus.CANCELLED);
    }

    
    /**
     * submit required_action数据，其他内部工具执行状态修改完毕后才可提交外部工具结果
     */
    @Transactional
    public boolean submitRequiredAction(String threadId, String runId, SubmitToolOutputs submitToolOutputs, LocalDateTime expiredAt) {
        // 需要确保执行实例已经退出，目的是确保一个run只能在一个实例上执行，且其他内部工具执行状态修改完毕
        String instantId = serviceMesh.getRunningRunInstanceId(runId);
        if(instantId != null) {
            if (expiredAt.isBefore(LocalDateTime.now())) {
                throw new IllegalStateException("This run is running in the other instance with id: " + runId);
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return submitRequiredAction(threadId, runId, submitToolOutputs, expiredAt);
        }
        RunStepDb runStepDb = runStepRepo.findActionRequiredForUpdate(threadId, runId);

        RunStatus currentStatus = RunStatus.fromValue(runStepDb.getStatus());
        if(!currentStatus.canTransitionTo(RunStatus.COMPLETED)) {
            logger.warn("Invalid status transition: {} -> {}", currentStatus, RunStatus.COMPLETED);
            throw new RuntimeException("Invalid status transition, current status is " + currentStatus);
        }
        StepDetails stepDetails = JacksonUtils.deserialize(runStepDb.getStepDetails(), StepDetails.class);
        Map<String, ToolCall> outputMap = submitToolOutputs.getToolCalls().stream().collect(Collectors.toMap(ToolCall::getId, toolCall -> toolCall));
        for(ToolCall toolCall : stepDetails.getToolCalls()) {
            if(toolCall.getId() == null) {
                throw new BizParamCheckException("tool call Id is null");
            }
            if(outputMap.containsKey(toolCall.getId())) {
                 if(toolCall.getFunction() != null) {
                    String output = outputMap.get(toolCall.getId()).getFunction().getOutput();
                    toolCall.getFunction().setOutput(StringUtils.isBlank(output) ? "未获取到结果" : output);
                }
            } else {
                throw new BizParamCheckException("tool call id is not exist");
            }
        }
        runStepDb.setStepDetails(JacksonUtils.serialize(stepDetails));
        runStepDb.setCompletedAt(LocalDateTime.now());
        runStepDb.setStatus(RunStatus.COMPLETED.getValue());
        runStepRepo.update(runStepDb);
        runRepo.updateRequireAction(threadId, runId, null);
        return updateRunStatus(threadId, runId, RunStatus.QUEUED);
    }

    /**
     * 处理cancel事件
     */
    public void cancel(Event event) {
        String runId = event.getPayload();
        if(runId == null) {
            return;
        }

        ExecutionContext context = processingCache.getIfPresent(runId);
        if(context == null) {
            return;
        }
        context.cancel();
    }

    /**
     * 开始工具调用
     */
    @Transactional
    public void startToolCalls(ExecutionContext context, String reasoning, Usage usage) {
        RunStepDb db = createToolCallsRunStep(context, reasoning, usage);
        context.setCurrentToolCallStepId(db.getId());
        // 开启工具调用
        context.signalToolCall();
    }


    /**
     * 创建工具调用RunStep
     */
    private RunStepDb createToolCallsRunStep(ExecutionContext context, String reasoning, Usage usage) {
        RunStepDb runStep = new RunStepDb();
        runStep.setRunId(context.getRunId());
        runStep.setThreadId(context.getThreadId());
        runStep.setAssistantId(context.getAssistantId());
        runStep.setType("tool_calls");
        runStep.setStatus("in_progress");
        runStep.setCreatedAt(LocalDateTime.now());

        // 构建初始的step_details（不含output）
        String stepDetails = buildInitialStepDetails(Lists.newArrayList(context.getCurrentToolTasks().values()));
        runStep.setStepDetails(stepDetails);

        if(usage != null) {
            runStep.setUsage(JacksonUtils.serialize(usage));
        }

        return runStepRepo.insert(runStep);
    }

    /**
     * 构建初始步骤详情（工具调用但无output）
     */
    private String buildInitialStepDetails(List<ChatToolCall> toolCalls) {
        StepDetails stepDetails = StepDetails.builder()
                .type("tool_calls")
                .toolCalls(convertToToolCalls(toolCalls))
                .build();
        return JacksonUtils.serialize(stepDetails);
    }

    /**
     * 完成内部工具调用
     */
    @Transactional
    public void finishToolCall(ExecutionContext context, ToolCall toolCall, String errorMessage) {
        if(context.getCurrentToolCallStepId() == null) {
            logger.warn("no current tool call step");
            return;
        }
        String stepId = context.getCurrentToolCallStepId();
        context.finishToolCall(toolCall);
        List<ToolCall> results = Lists.newArrayList(context.getCurrentToolResults());
        // 构建step_details
        StepDetails stepDetails = StepDetails.builder()
                .type("tool_calls")
                .toolCalls(results)
                .build();
        String stepDetailsJson = JacksonUtils.serialize(stepDetails);
        runStepRepo.updateStepDetails(context.getThreadId(), stepId, stepDetailsJson);
        if(errorMessage != null) {
            LastError lastError = new LastError("tool_execute_error", errorMessage);
            updateRunStepStatus(stepId, RunStatus.FAILED, lastError, context);
        }
        if(!context.hasInProgressToolCalls() && results.size() == context.getCurrentToolResults().size()) {
            boolean signal = true;
            if(errorMessage == null) {
                signal = updateRunStepStatus(stepId, RunStatus.COMPLETED, null, context);
            }
            context.setCurrentToolCallStepId(null);
            if(signal) {
                context.signalRunner();
            }
        }
    }

    /**
     * 修改runStep的状态
     */
    @Transactional
    public boolean updateRunStepStatus(String runStepId, RunStatus newStatus, LastError lastError, ExecutionContext context) {
        return updateRunStep(runStepId, newStatus, lastError, context, null);
    }

    @Transactional
    public boolean updateRunStep(String runStepId, RunStatus newStatus, LastError lastError, ExecutionContext context, Usage usage) {
        if(runStepId == null) {
            return false;
        }
        RunStepDb db = runStepRepo.findByIdForUpdate(context.getThreadId(), runStepId);

        // 失败时先修改message的状态
        if(newStatus.isTerminal() && db.getType().equals("message_creation")) {
            if(newStatus != RunStatus.COMPLETED) {
                updateMessageStatus(context, "incomplete", context.isNoExecute());
            } else {
                updateMessageStatus(context, "completed", context.isNoExecute());
            }
        }

        RunStatus currentStatus = RunStatus.fromValue(db.getStatus());
        if(!currentStatus.canTransitionTo(newStatus)) {
            logger.warn("Invalid status transition for run step {}: {} -> {}",
                    runStepId, currentStatus, newStatus);
            return false;
        }

        // 更新状态
        db.setStatus(newStatus.getValue());
        if(lastError != null) {
            db.setLastError(JacksonUtils.serialize(lastError));
        }
        db.setUpdatedAt(LocalDateTime.now());

        // 如果是终止状态，设置完成时间
        if (newStatus.isTerminal()) {
            db.setCompletedAt(LocalDateTime.now());
        }

        if(newStatus.isCanceled()) {
            db.setCancelledAt(LocalDateTime.now());
        }

        if(usage != null) {
            db.setUsage(JacksonUtils.serialize(usage));
        }

        boolean success = runStepRepo.update(db);
        if(db.getType().equals("message_creation")) {
            context.getCurrentRunStep().setStatus(newStatus.getValue());
        }
        if(newStatus.getRunStepStreamEvent() != null && db.getType().equals("message_creation")) {
            // 发送客户端消息
            RunStep runStep = RunUtils.convertStepToInfo(db);
            context.publish(runStep);
        }
        return success;
    }

    /**
     * 转换ChatToolCall为ToolCall
     */
    private List<ToolCall> convertToToolCalls(List<ChatToolCall> chatToolCalls) {
        List<ToolCall> toolCalls = new ArrayList<>();
        for (ChatToolCall chatToolCall : chatToolCalls) {
            toolCalls.add(MessageUtils.convertToolCall(chatToolCall));
        }
        return toolCalls;
    }

    /**
     * 完成Assistant Message
     */
    @Transactional
    public void finishMessageCreation(ExecutionContext context, MessageContent content, String reasoning, Usage usage) {
        addContent(context, content, reasoning);
        updateRunStep(context.getCurrentRunStep().getId(), RunStatus.COMPLETED, null, context, usage);
        // 创建助手消息，意味着llm处理结束，开启下一轮planning
        context.signalRunner();
    }

    @Transactional
    public Message updateMessageStatus(ExecutionContext context, String status, boolean hidden) {
        Message message = messageService.updateStatus(context.getThreadId(), context.getAssistantMessageId(), status, hidden);
        context.publish(message);
        return message;
    }


    @Transactional
    public Message addContent(ExecutionContext context, MessageContent content, String reasoning) {
        return messageService.addContent(context.getThreadId(), context.getAssistantMessageId(), content, reasoning);
    }


    /**
     * 将Run Step状态转换为需要操作
     */
    @Transactional
    public boolean addRequiresAction(ExecutionContext context, RequiredAction requiredAction) {
        if(context.getCurrentToolCallStepId() == null) {
            logger.warn("no current tool call step");
            return false;
        }
        updateRunStepStatus(context.getCurrentToolCallStepId(), RunStatus.REQUIRES_ACTION, null, context);
        context.requiredAction(requiredAction);
        return true;
    }
}
