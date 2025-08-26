package com.ke.assistant.core.run;

import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.core.TaskExecutor;
import com.ke.assistant.core.ai.ChatService;
import com.ke.assistant.core.plan.Planner;
import com.ke.assistant.core.plan.PlannerDecision;
import com.ke.assistant.core.tools.ToolExecutor;
import com.ke.assistant.core.tools.ToolFetcher;
import com.ke.assistant.service.RunService;
import com.ke.assistant.service.ThreadService;
import com.theokanning.openai.assistants.run.Run;
import com.theokanning.openai.assistants.run_step.RunStep;
import com.theokanning.openai.assistants.run_step.StepDetails;
import com.theokanning.openai.assistants.thread.Thread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Run执行器
 * 负责Run的完整执行流程，包含执行循环逻辑
 */
@Component
public class RunExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(RunExecutor.class);
    
    @Autowired
    private Planner planner;

    @Autowired
    private ToolFetcher toolFetcher;

    @Autowired
    private ChatService chatService;

    @Autowired
    private RunStateManager stateManager;

    @Autowired
    private RunService runService;

    @Autowired
    private ThreadService threadService;

    @Autowired
    private AssistantProperties assistantProperties;

    /**
     * 开启run
     */
    public void startRun(String threadId, String runId, String assistantMessageId, boolean withThreadCreation, SseEmitter sseEmitter) {
        ExecutionContext context = buildExecutionContext(threadId, runId, assistantMessageId, withThreadCreation ? RunType.CREATE_THREAD_AND_RUN : RunType.CREATE_RUN);
        TaskExecutor.addRunner(()->executeRun(context, sseEmitter));
    }

    /**
     * 重启run
     */
    public void resumeRun(String threadId, String runId, String assistantMessageId, SseEmitter sseEmitter) {
        ExecutionContext context = buildExecutionContext(threadId, runId, assistantMessageId, RunType.SUBMIT_TOOL_CALLS);
        TaskExecutor.addRunner(()->executeRun(context, sseEmitter));
    }


    /**
     * 执行Run
     * 包含完整的执行循环逻辑
     */
    private void executeRun(ExecutionContext context, SseEmitter sseEmitter) {
        logger.info("Starting execution for run: {}", context.getRunId());

        try {

            // 启动消息管理器
            MessageExecutor.start(context, stateManager, sseEmitter);

            // 启动工具执行器
            ToolExecutor.start(context, stateManager, toolFetcher);

            // 构建执行上下文
            if(context.isError()) {
                stateManager.toFailed(context);
                return;
            }

            // 更新状态为执行中
            if(!stateManager.toInProgress(context)) {
                logger.error("Failed to update run status to IN_PROGRESS: {}", context.getRunId());
                return;
            }

            // 主执行循环
            executeLoop(context);

            // 发送 "DONE", 并确认发送完成
            context.publish("[DONE]");
            if(!context.isFinishSend()) {
                context.waitForSend(10);
            }
        } finally {
            // 通知所有辅助线程退出
            context.end();
        }
    }
    
    /**
     * 主执行循环
     */
    private void executeLoop(ExecutionContext context) {
        String runId = context.getRunId();

        PlannerDecision decision = PlannerDecision.init();
        
        while (decision != null && decision.needExecution()) {
            try {
                context.incrementStep();
                logger.debug("Executing step {} for run: {}", context.getCurrentStep(), runId);
                
                // 1. 规划下一步
                decision = planner.nextStep(context);
                if (decision == null) {
                    logger.error("Failed to plan next step for run: {}", runId);
                    context.setError("server_error", "Planning failed");
                    stateManager.toFailed(context);
                    break;
                }
                
                // 2. 执行决策
                switch (decision.getAction()) {
                case COMPLETE:
                    stateManager.toCompleted(context);
                    break;
                case CANCELED:
                    stateManager.toCanceled(context);
                    break;
                case ERROR:
                    stateManager.toFailed(context);
                    break;
                case WAIT_FOR_INPUT:
                    stateManager.toRequiresAction(context);
                    break;
                case EXPIRED:
                    stateManager.toExpired(context);
                    break;
                // 以上全部代表结束
                case LLM_CALL:
                    chatService.chat(context);
                    context.runnerAwait();
                    break;
                case WAIT_FOR_TOOL:
                    context.signalToolCall();
                    context.runnerAwait();
                    break;
                }


            } catch (Exception e) {
                logger.error("Error in execution loop for run: {}, step: {}", runId, context.getCurrentStep(), e);
                context.setError("server_error", e.getMessage());
                stateManager.toFailed(context);
                break;
            }
        }
    }

    
    /**
     * 构建执行上下文
     */
    private ExecutionContext buildExecutionContext(String threadId, String runId, String assistantMessageId, RunType type) {

        ExecutionContext context = new ExecutionContext();

        context.setAssistantMessageId(assistantMessageId);

        try {

            if(assistantProperties.getMaxExecutionMinutes() != null ) {
                context.setExpiredAt(LocalDateTime.now().plusMinutes(assistantProperties.getMaxExecutionMinutes()));
            }

            context.setMaxSteps(assistantProperties.getMaxExecutionSteps() == null ? 50 : assistantProperties.getMaxExecutionSteps());

            // 获取Run信息
            Run run = runService.getRunById(threadId, runId);
            if (run == null) {
                context.setError("not_found", "Run not found");
                logger.error("Run not found: {}", runId);
                return context;
            }
            context.setRun(run);

            // 工具
            context.setTools(run.getTools());

            context.setToolFiles(run.getFileIds());

            // 获取当前的runStep，时间从小到大
            List<RunStep> runSteps = runService.getRunSteps(threadId, runId);

            RunStep currentStep = null;

            // 找到最后一个message_creation，当前策略只会有一个message_creation
            for(RunStep step : runSteps) {
                if("message_creation".equals(step.getType())) {
                    currentStep = step;
                }
                if("tool_calls".equals(step.getType())) {
                    RunStatus status = RunStatus.fromValue(step.getStatus());
                    StepDetails details = step.getStepDetails();
                    if(details.getToolCalls() != null) {
                        // 需要构建上下文只有run开启和提交工具结果两种情况，工具的调用的step，一定是终止状态
                        if(status.isTerminal()) {
                            context.addHistoryToolStep(step);
                        }
                    }
                }
            }
            context.setCurrentRunStep(currentStep);

            //添加要发送的消息
            if(!"queued".equals(run.getStatus())) {
                throw new IllegalStateException("invalid run step status");
            }

            if(type == RunType.CREATE_THREAD_AND_RUN) {
                Thread thread = threadService.getThreadById(context.getThreadId());
                context.publish(thread);
            }

            if(type != RunType.SUBMIT_TOOL_CALLS) {
                context.publish(run);
            } else {
                RunStep runStep = context.getLastToolCallStep();
                if(runStep == null || !"completed".equals(runStep.getStatus())) {
                    throw new IllegalStateException("invalid run step status");
                }
                context.publish(new ResumeMessage(run, runStep));
            }

            return context;
            
        } catch (Exception e) {
            logger.error("Failed to build execution context for run: {}", runId, e);
            throw e;
        }
    }


    enum RunType {
        CREATE_RUN,
        CREATE_THREAD_AND_RUN,
        SUBMIT_TOOL_CALLS
    }

}
