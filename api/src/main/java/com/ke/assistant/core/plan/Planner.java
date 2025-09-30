package com.ke.assistant.core.plan;

import com.ke.assistant.core.memory.ContextTruncator;
import com.ke.assistant.core.plan.template.TemplateContext;
import com.ke.assistant.core.plan.template.TemplateContextBuilder;
import com.ke.assistant.core.run.ExecutionContext;
import com.ke.assistant.core.tools.ToolFetcher;
import com.ke.assistant.core.tools.handlers.definition.CustomToolHandler;
import com.ke.assistant.service.MessageService;
import com.ke.assistant.service.RunService;
import com.ke.assistant.util.MessageUtils;
import com.ke.assistant.util.MetaConstants;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.Renders;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.message.MessageContent;
import com.theokanning.openai.assistants.run_step.RunStep;
import com.theokanning.openai.assistants.run_step.StepDetails;
import com.theokanning.openai.completion.chat.AssistantMultipleMessage;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatTool;
import com.theokanning.openai.completion.chat.SystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 提供规划决策逻辑
 */
@Component
public class Planner {

    @Autowired
    private MessageService messageService;

    @Autowired
    private ToolFetcher toolFetcher;

    @Autowired
    private RunService runService;

    @Autowired
    private ContextTruncator truncator;

    private static final Logger logger = LoggerFactory.getLogger(Planner.class);


    /**
     * 规划下一步动作
     *
     * @param context 执行上下文（包含所有必要信息）
     * @return 规划决策结果
     */
    public PlannerDecision nextStep(ExecutionContext context) {
        try {
            logger.debug("Planning next step for run: {}, step: {}",
                    context.getRunId(), context.getCurrentStep());

            // 检查是否被取消
            if(context.isCanceled()) {
                return PlannerDecision.canceled("Run has been canceled");
            }

            // 检查异常
            if(context.isError()) {
                return PlannerDecision.error(context.getLastError().getMessage());
            }

            // 检查完成条件
            if (isCompleted(context)) {
                return PlannerDecision.complete("All tasks completed");
            }

            // 检查等待外部输入
            if (context.isRequiredAction()) {
                return PlannerDecision.waitForInput("Wait for input");
            }

            // 检查超时
            if(context.isTimeout()) {
                return PlannerDecision.expired("Execute time out");
            }

            // 检查是否超过最大步数
            if (context.exceedsMaxSteps()) {
                context.setError("exceeded_max_steps", "Exceeded maximum steps: " + context.getMaxSteps());
                return PlannerDecision.expired("Exceeded maximum steps: " + context.getMaxSteps());
            }

            // 检查是否需要等待
            if (needsWaiting(context)) {
                return PlannerDecision.waitForTool("Waiting for tool call responses");
            }

            // 决策下一步动作
            return planNextAction(context);

        } catch (Exception e) {
            logger.error("Failed to plan next step for run: {}", context.getRunId(), e);
            context.setError("server_error", e.getMessage());
            return PlannerDecision.error(e.getMessage());
        }
    }


    /**
     * 检查执行是否已完成
     *
     * @param context 执行上下文
     * @return 是否已完成
     */
    public boolean isCompleted(ExecutionContext context) {
        // 简单的完成条件检查
        if (context.isCompleted()) {
            return true;
        }

        RunStep lastStep = context.getCurrentRunStep();
        if (lastStep == null) {
            return false;
        }
        // 检查最后的RunStep是否是消息创建且已完成
        return "message_creation".equals(lastStep.getType()) &&
                "completed".equals(lastStep.getStatus());
    }

    /**
     * 检查是否有等待中的工具调用
     *
     * @param context 执行上下文
     * @return 是否有等待中的工具调用
     */
    public boolean needsWaiting(ExecutionContext context) {
        return context.hasInProgressToolCalls();
    }

    /**
     * 规划下一步动作
     */
    private PlannerDecision planNextAction(ExecutionContext context) {
        // 构建聊天消息
        buildChatMessages(context);
        // 构建工具列表
        buildChatTools(context);
        // 处理上下文
        if(context.isStore() && !context.isDisableTruncate()) {
            truncator.truncate(context);
        }
        // 获取当前消息
        List<ChatMessage> messages = context.getChatMessages();

        if (messages.isEmpty()) {
            context.setError("bad_request", "No messages to process");
            return PlannerDecision.error("No messages to process");
        }

        ChatMessage lastMessage = messages.get(messages.size() - 1);

        // 如果最后一条消息是assistant消息，检查是否有工具调用
        if ("assistant".equals(lastMessage.getRole())) {
            // 检查是否有待执行的工具调用
            if (hasUnexecutedToolCalls(context)) {
                return PlannerDecision.waitForTool("Waiting for tool call responses");
            } else {
                // 没有工具调用，对话已完成
                return PlannerDecision.complete("Conversation completed");
            }
        }

        // 如果最后一条消息是用户消息，或者工具消息，需要生成assistant回复
        return planLLMCall();
    }

    /**
     * 规划LLM调用
     */
    private PlannerDecision planLLMCall() {
        return PlannerDecision.builder().action(PlannerAction.LLM_CALL)
                .reason("Generate assistant response")
                .build();
    }

    /**
     * 构建聊天消息列表
     */
    private void buildChatMessages(ExecutionContext context) {
        // 如果不是第一次构建，只需要把工具相关的消息加入
        if(!context.getChatMessages().isEmpty()) {
            if(context.getCurrentToolResults() != null && !context.getCurrentToolResults().isEmpty()) {
                MessageUtils.convertToolCallMessages(context.getCurrentToolResults(), null, context.getCurrentMetaData(), context.isSupportReasonInput()).forEach(context::addChatMessage);
                context.clearCurrentRunStepCache();
            }
            return;
        }

        // 使用模板渲染系统指令
        String renderedSystemPrompt = renderSystemPrompt(context);
        if (renderedSystemPrompt != null && !renderedSystemPrompt.trim().isEmpty()) {
            context.addChatMessage(new SystemMessage(renderedSystemPrompt));
        }

        // 消息历史，只返回在此次run之前的消息，additional messages在run之后创建因此不包含在内
        List<Message> messages = messageService.getMessagesForRun(context.getThreadId(), context.getRun().getCreateTime());

        // 此次run的additional messages，因为存在不保存的消息，需要单独添加
        if(context.getAdditionalMessages() != null && !context.getAdditionalMessages().isEmpty()) {
            messages.addAll(context.getAdditionalMessages());
        }

        // 线程下的所有RunSteps
        Map<String, List<RunStep>> runStepMap = runService.getThreadSteps(context.getThreadId()).stream().collect(Collectors.groupingBy(RunStep::getRunId));

        Message lastAssistantMessage = null;
        ChatMessage lastAssistantChatMessage = null;

        for(Message message : messages) {
            if(message.getRole().equals("user")) {
                ChatMessage chatMessage = MessageUtils.formatChatCompletionMessage(message, context.getFileInfos(), context.isVisionModel());
                if(chatMessage != null) {
                    context.addChatMessage(chatMessage);
                }
            } else if(message.getRole().equals("assistant")) {
                ChatMessage assistantMessage = MessageUtils.formatChatCompletionMessage(message, context.getFileInfos(), context.isVisionModel());
                if(message.getRunId() != null && runStepMap.containsKey(message.getRunId())) {
                    for (RunStep runStep : runStepMap.get(message.getRunId())) {
                        buildToolMessage(context, runStep);
                    }
                }
                context.addChatMessage(assistantMessage);
                lastAssistantMessage = message;
                lastAssistantChatMessage = assistantMessage;
            } else if(message.getRole().equals("tool")) {
                message.getContent().stream().map(MessageContent::getToolResult).forEach(context::addChatMessage);
            }
        }

        // 将之前的工具调用结果加入
        if(context.getHistoryToolSteps() != null && !context.getHistoryToolSteps().isEmpty()) {
            for (RunStep runStep : context.getHistoryToolSteps()) {
                StepDetails stepDetails = runStep.getStepDetails();
                if(stepDetails.getToolCalls() == null) {
                    continue;
                }
                MessageUtils.convertToolCallMessages(stepDetails.getToolCalls(), runStep.getLastError(), runStep.getMetadata(), context.isSupportReasonInput()).forEach(context::addChatMessage);
            }
        } else if(context.isSupportReasonInput()){
            // 如果最后一条assistant message为tool call，且开启了推理输出
            // 对于某些模型必须添加思考过程，通过historyToolStep构建时，一定是当前run轮次的工具调用，会自动添加，不需要再次判断
            // 非tool call则不添加，节省tokens
            if(lastAssistantChatMessage instanceof AssistantMultipleMessage) {
                AssistantMultipleMessage chatMsg = (AssistantMultipleMessage) lastAssistantChatMessage;
                if(chatMsg.getToolCalls() != null && !chatMsg.getToolCalls().isEmpty()) {
                    chatMsg.setReasoningContent(lastAssistantMessage.getReasoningContent());
                    chatMsg.setReasoningContentSignature(lastAssistantMessage.getMetadata().get(MetaConstants.REASONING_SIG));
                    chatMsg.setRedactedReasoningContent(lastAssistantMessage.getMetadata().get(MetaConstants.REDACTED_REASONING));
                    if(chatMsg.getRedactedReasoningContent() == null && (chatMsg.getReasoningContent() == null || chatMsg.getReasoningContentSignature() == null)) {
                        context.setReasoningShutDown(true);
                    }
                }
            }
        }
    }

    private void buildToolMessage(ExecutionContext context, RunStep runStep) {
        StepDetails stepDetails = runStep.getStepDetails();
        if(stepDetails == null) {
            return;
        }
        // 非当前run轮次的工具调用不需要带上思考过程
        if("tool_calls".equals(stepDetails.getType())) {
            MessageUtils.convertToolCallMessages(stepDetails.getToolCalls(), runStep.getLastError(), runStep.getMetadata(), false).forEach(context::addChatMessage);
        }
    }

    /**
     * 构建工具列表
     */
    private void buildChatTools(ExecutionContext context) {
        if(!context.getChatTools().isEmpty()) {
            return;
        }
        if(context.getTools().isEmpty()) {
            return;
        }
        for(Tool tool : context.getTools()) {
            ChatTool chatTool;
            if(tool instanceof Tool.Function) {
                chatTool = new ChatTool();
                Tool.Function function = (Tool.Function) tool;
                chatTool.setFunction(function.getFunction());
            } else if(tool instanceof Tool.Custom) {
                chatTool = new ChatTool();
                Tool.Custom custom = (Tool.Custom) tool;
                Tool.FunctionDefinition definition = new Tool.FunctionDefinition();
                definition.setName(CustomToolHandler.getToolName(custom.getDefinition()));
                definition.setDescription(CustomToolHandler.getDescription(custom.getDefinition()));
                definition.setParameters(CustomToolHandler.getParameters(custom.getDefinition()));
                definition.setStrict(true);
                chatTool.setFunction(definition);
            } else {
                chatTool = toolFetcher.fetchChatTool(tool.getType());
            }
            context.addChatTool(chatTool);
        }
    }

    /**
     * 检查是否有未执行的工具调用
     */
    private boolean hasUnexecutedToolCalls(ExecutionContext context) {
        return context.hasInProgressToolCalls();
    }

    /**
     * 渲染系统提示词模板
     *
     * @param context 执行上下文
     * @return 渲染后的系统提示词
     */
    private String renderSystemPrompt(ExecutionContext context) {
        try {
            // 构建模板上下文
            TemplateContext templateContext = TemplateContextBuilder.buildTemplateContext(context);
            
            // 使用模板服务渲染系统提示词
            return Renders.render("templates/planner_prompt.pebble", JacksonUtils.toMap(templateContext));
            
        } catch (Exception e) {
            logger.warn("Failed to render system prompt template for run: {}, falling back to original instructions", 
                       context.getRunId(), e);
            
            // 降级处理：如果模板渲染失败，使用原始的指令
            String instructions = context.getInstructions();
            return instructions != null ? instructions : "";
        }
    }

}
