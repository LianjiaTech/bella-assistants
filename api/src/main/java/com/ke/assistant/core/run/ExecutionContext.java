package com.ke.assistant.core.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ke.assistant.core.file.FileInfo;
import com.ke.assistant.util.MetaConstants;
import com.ke.bella.openapi.protocol.completion.CompletionModelFeatures;
import com.ke.bella.openapi.protocol.completion.CompletionModelProperties;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.Usage;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.run.RequiredAction;
import com.theokanning.openai.assistants.run.Run;
import com.theokanning.openai.assistants.run.ToolCall;
import com.theokanning.openai.assistants.run.ToolChoice;
import com.theokanning.openai.assistants.run.ToolFiles;
import com.theokanning.openai.assistants.run_step.RunStep;
import com.theokanning.openai.common.LastError;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatTool;
import com.theokanning.openai.completion.chat.ChatToolCall;
import com.theokanning.openai.response.Response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Run执行上下文
 * 包含执行过程中所需的所有状态信息，用于在plan、tool执行、run执行之间传递信息
 */
@Data
@AllArgsConstructor
@Builder
public class ExecutionContext {

    // Bella相关的上下文
    private final Map<String, Object> bellaContext;

    // 用于同步executor之间的执行结果
    private final ReentrantLock lock;
    private final Condition runCondition;
    private final Condition sendCondition;
    private final Condition toolCondition;

    // 发送客户端消息的通知队列
    private final LinkedBlockingQueue<Object> senderQueue;
    // 发送客户端完成的标识
    private final AtomicBoolean sendDone;
    // 当前正在输出内容的toolCallId，确保不同工具执行并行，内容串行输出
    private final AtomicReference<String> currentOutputToolCallId;

    // run的执行线程的结束标识
    private final AtomicBoolean end;

    private final Supplier<String> toolCallStepIdSupplier;

    private Run run;
    private List<Tool> tools;
    private ToolFiles toolFiles;
    private Map<String, FileInfo> fileInfos;
    private RunStep currentRunStep;
    private String currentToolCallStepId;
    private Message assistantMessage;

    // 超时时间
    private LocalDateTime expiredAt;
    // 最大执行步骤
    private Integer maxSteps;

    // 执行状态
    private int currentStep;
    private boolean completed;
    private AtomicBoolean canceled;
    private AtomicReference<RequiredAction> requiredAction;
    private LastError lastError;
    private LocalDateTime startTime;
    private LocalDateTime lastUpdateTime;

    // 历史轮次的已完成工具执行记录，用于消息上下文的构建
    private List<RunStep> historyToolSteps;

    // 添加的additionalMessages
    private List<Message> additionalMessages;
    
    // 当前轮次的执行结果
    private final List<ChatMessage> chatMessages;                     // 当前聊天构建的消息
    private final List<ChatTool> chatTools;                          // 当前聊天构建的Tools
    // 工具并行，存在并发写入
    private final CopyOnWriteArrayList<ToolCall> currentToolResults;  // 当前工具调用的结果
    // 当前runStep的信息
    private final Map<String, String> currentMetaData;
    // {index, ChatToolCall}
    private final ConcurrentHashMap<Integer, ChatToolCall> currentToolTasks;  // 当前待执行的工具
    // 本次run的总消耗
    private Usage usage;
    // 是否未执行，决定本轮的assistant消息是否生效
    private boolean noExecute;
    // 模型特性
    private CompletionModelFeatures modelFeatures;
    // 模型属性
    private CompletionModelProperties modelProperties;
    
    // Response API相关字段
    private Response response; // 用于区分是否为Response API调用

    private CompletableFuture<Object> future; // 用于block模式，获取结果

    public ExecutionContext(Map<String, Object> bellaContext, Supplier<String> toolCallStepIdSupplier) {
        this.bellaContext = bellaContext;
        this.toolCallStepIdSupplier = toolCallStepIdSupplier;
        this.senderQueue = new LinkedBlockingQueue<>();
        this.sendDone = new AtomicBoolean(true);
        this.lock = new ReentrantLock();
        this.runCondition = lock.newCondition();
        this.sendCondition = lock.newCondition();
        this.toolCondition = lock.newCondition();
        this.currentStep = 0;
        this.completed = false;
        this.canceled = new AtomicBoolean(false);
        this.requiredAction = new AtomicReference<>();
        this.startTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
        this.chatMessages = new ArrayList<>();
        this.currentToolResults = new CopyOnWriteArrayList<>();
        this.currentMetaData = new HashMap<>();
        this.chatTools = new ArrayList<>();
        this.currentToolTasks = new ConcurrentHashMap<>();
        this.historyToolSteps = new ArrayList<>();
        this.end = new AtomicBoolean(false);
        this.currentOutputToolCallId = new AtomicReference<>();
        this.fileInfos = new HashMap<>();
        this.future = new CompletableFuture<>();
    }
    
    /**
     * 更新最后操作时间
     */
    public void touch() {
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 增加执行步数
     */
    public void incrementStep() {
        this.currentStep++;
        touch();
    }
    
    /**
     * 检查是否超过最大步数
     */
    public boolean exceedsMaxSteps() {
        return this.currentStep >= maxSteps;
    }
    
    /**
     * 检查是否有进行中的工具调用
     */
    public boolean hasInProgressToolCalls() {
        return !currentToolTasks.isEmpty();
    }


    /**
     * 添加消息
     */
    public void addChatMessage(ChatMessage chatMessage) {
        chatMessages.add(chatMessage);
    }

    /**
     * 工具执行完毕，添加工具执行结果，删除待执行工具
     */
    public void finishToolCall(ToolCall toolCall) {
        currentToolResults.add(toolCall);
        if(toolCall.getIndex() != null) {
            currentToolTasks.remove(toolCall.getIndex());
        } else if(!currentToolTasks.isEmpty()) {
            Integer index = currentToolTasks.entrySet().stream().filter(e -> e.getValue().getId().equals(toolCall.getId())).map(Map.Entry::getKey).findAny().orElse(null);
            if(index != null) {
                currentToolTasks.remove(toolCall.getIndex());
            }
        }
    }

    /**
     * 清除当前runStep的缓存信息
     */
    public void clearCurrentRunStepCache() {
        currentToolResults.clear();
        currentMetaData.clear();
    }

    /**
     * 添加工具
     */
    public void addChatTool(ChatTool chatTool) {
        chatTools.add(chatTool);
    }

    /**
     * 添加当前待执行的工具
     * @param chatToolCall
     */
    public void addToolCallTask(ChatToolCall chatToolCall) {
        if(currentToolTasks.containsKey(chatToolCall.getIndex())) {
            ChatToolCall origin = currentToolTasks.get(chatToolCall.getIndex());
            if(origin.getFunction() == null) {
                origin.setFunction(chatToolCall.getFunction());
            } else {
                origin.getFunction().setName(
                        Optional.ofNullable(origin.getFunction().getName()).orElse("") + Optional.ofNullable(chatToolCall.getFunction().getName())
                                .orElse(""));
                JsonNode argNodes = Optional.ofNullable(
                        chatToolCall.getFunction().getArguments()).orElse(new TextNode(""));
                String args;
                if(argNodes instanceof TextNode) {
                    args = argNodes.asText();
                } else {
                    args = JacksonUtils.serialize(argNodes);
                }
                origin.getFunction().setArguments(new TextNode(
                        Optional.ofNullable(origin.getFunction().getArguments()).orElse(new TextNode("")).asText() + args));
            }
        } else {
            currentToolTasks.put(chatToolCall.getIndex(), chatToolCall);
        }
    }

    /**
     * 添加工具执行的历史
     */
    public void addHistoryToolStep(RunStep runStep) {
        historyToolSteps.add(runStep);
        incrementStep();
    }

    /**
     * 获取runId
     */
    public String getRunId() {
        return run.getId();
    }

    /**
     * 获取User
     */
    public String getUser() {
        return run.getUser();
    }

    /**
     * 获取ThreadId
     */
    public String getThreadId() {
        return run.getThreadId();
    }

    /**
     * 获取AssistantID
     */
    public String getAssistantId() {
        return run.getAssistantId();
    }

    /**
     * 获取模型名称
     */
    public String getModel() {
         return run.getModel();
    }

    /**
     * 获取指令内容
     */
    public String getInstructions() {
        return run.getInstructions();
    }

    /**
     * 获取温度参数
     */
    public double getTemperature() {
        return run.getTemperature();
    }

    /**
     * 获取top_p参数
     */
    public Double getTopP() {
        return run.getTopP();
    }
    
    /**
     * 获取最大完成token数
     */
    public Integer getMaxCompletionTokens() {
        if(run.getMaxCompletionTokens() == null || run.getMaxCompletionTokens() > 0) {
            return run.getMaxCompletionTokens();
        }
        return null;
    }
    
    /**
     * 获取工具选择模式
     */
    public ToolChoice getToolChoice() {
        if (run.getToolChoice() != null) {
            return run.getToolChoice();
        }
        return ToolChoice.AUTO; // 默认自动选择
    }

    public boolean isError() {
        return lastError != null;
    }

    /**
     * 异常
     */
    public void setError(String code, String message) {
        lastError = new LastError();
        lastError.setCode(code);
        lastError.setMessage(message);
        signalRunner();
    }

    /**
     * 是否超时
     */
    public boolean isTimeout() {
        if (expiredAt == null) {
            return false;
        }
        return expiredAt.isBefore(LocalDateTime.now());
    }

    /**
     * 剩余时间
     */
    public Integer getExecutionSeconds() {
        return Math.toIntExact(expiredAt.toEpochSecond(ZoneOffset.ofHours(8)) - DateTimeUtils.getCurrentSeconds());
    }

    /**
     * RunExecutor 等待
     */
    public void runnerAwait() {
        await(getExecutionSeconds(), runCondition);
    }


    /**
     * 唤醒 RunExecutor
     */
    public void signalRunner() {
        signal(runCondition);
    }

    /**
     * ToolExecutor 等待
     */
    public void toolCallAwait() {
        await(getExecutionSeconds(), toolCondition);
    }


    /**
     * 唤醒 ToolExecutor
     */
    public void signalToolCall() {
        signal(toolCondition);
    }

    /**
     * 取消
     */
    public void cancel() {
        canceled.set(true);
        signalRunner();
    }

    /**
     * 是否取消
     */
    public boolean isCanceled() {
        return canceled.get();
    }

    /**
     * 等待外部输入
     */
    public void requiredAction(RequiredAction requiredAction) {
        this.requiredAction.set(requiredAction);
        signalRunner();
    }

    /**
     * 是否取消
     */
    public boolean isRequiredAction() {
        return requiredAction.get() != null;
    }

    /**
     * 发布消息 - 无界队列，一定成功
     */
    public void publish(Object msg) {
        senderQueue.offer(msg);
    }

    /**
     * 获取消息 - 阻塞
     */
    public Object consume() {
        try {
            return senderQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * 是否完成发送
     */
    public boolean isFinishSend() {
        return sendDone.get();
    }

    /**
     * 发送完成
     */
    public void finishSend() {
        sendDone.set(true);
        signal(sendCondition);
    }

    /**
     * 等待发送消息
     */
    public void waitForSend(int maxSeconds) {
        await(maxSeconds, sendCondition);
    }

    /**
     * await
     * @param seconds
     * @param condition
     */
    private void await(int seconds, Condition condition) {
        lock.lock();
        try {
            condition.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    /**
     * signal
     * @param condition
     */
    private void signal(Condition condition) {
        try {
            lock.lock();
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * run的执行线程是否结束
     * @return
     */
    public boolean isEnd() {
        return end.get();
    }

    /**
     * 结束时，进行通知
     */
    public void end() {
        end.set(true);
        // 唤醒工具执行线程
        signal(toolCondition);
        // 唤醒消息执行线程
        publish("[END]");
    }

    /**
     * 获取当前正在输出的工具ID
     */
    public String getCurrentOutputToolCallId() {
        return currentOutputToolCallId.get();
    }

    /**
     * 设置当前正在输出的工具ID
     */
    public void setCurrentOutputToolCallId(String toolCallId) {
        currentOutputToolCallId.set(toolCallId);
    }

    /**
     * 输出结束：清除当前输出的工具ID
     */
    public void finishToolCallOutput() {
        currentOutputToolCallId.set(null);
    }

    /**
     * 获取最后一个ToolCall RunStep
     */
    public RunStep getLastToolCallStep() {
        if(historyToolSteps.isEmpty()) {
            return null;
        } else {
            return historyToolSteps.get(historyToolSteps.size() - 1);
        }
    }

    public void addUsage(Usage usage) {
        if(usage == null) {
            return;
        }
        if(this.usage == null) {
            this.usage = usage;
        } else {
            this.usage.add(usage);
        }
    }

    public boolean isVisionModel() {
        return modelFeatures == null || modelFeatures.isVision();
    }

    public boolean isSupportReasonInput() {
        if(run.getReasoningEffort() == null) {
            return false;
        }
        return modelFeatures == null || modelFeatures.isReason_content_input();
    }

    public String getAssistantMessageId() {
        Assert.notNull(assistantMessage, "assistantMessage is null");
        return assistantMessage.getId();
    }

    public synchronized boolean generateCurrentToolCallStepId() {
        if(this.getCurrentToolCallStepId() == null) {
            this.setCurrentToolCallStepId(toolCallStepIdSupplier.get());
            return true;
        }
        return false;
    }
    
    /**
     * 判断是否为Response API调用
     */
    public boolean isResponseApi() {
        return response != null;
    }

    public Object blockingGetResult(long seconds) throws ExecutionException, InterruptedException, TimeoutException {
        return future.get(seconds, TimeUnit.SECONDS);
    }

    public void complete(Object response) {
        future.complete(response);
    }

    public boolean isStore() {
        return !Boolean.FALSE.toString().equals(run.getMetadata().get(MetaConstants.STORE));
    }

    public boolean isHidden() {
        return !isStore() || isNoExecute();
    }

    public boolean isDisableTruncate() {
        return run.getTruncationStrategy() != null && run.getTruncationStrategy().getType().equals("disable");
    }

}
