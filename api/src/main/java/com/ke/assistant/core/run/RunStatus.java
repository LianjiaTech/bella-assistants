package com.ke.assistant.core.run;

import com.theokanning.openai.assistants.StreamEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * Run执行状态枚举
 * 基于OpenAI Assistant API标准定义
 */
@Getter
@AllArgsConstructor
public enum RunStatus {

    /**
     * 排队中 - Run已创建但尚未开始执行
     */
    QUEUED("queued", StreamEvent.THREAD_RUN_QUEUED, null),
    
    /**
     * 执行中 - Run正在执行中
     */
    IN_PROGRESS("in_progress", StreamEvent.THREAD_RUN_IN_PROGRESS, StreamEvent.THREAD_RUN_STEP_IN_PROGRESS),
    
    /**
     * 需要用户操作 - Run等待用户提供工具调用输出或其他输入
     */
    REQUIRES_ACTION("requires_action", StreamEvent.THREAD_RUN_REQUIRES_ACTION, null),
    
    /**
     * 取消中 - Run正在被取消
     */
    CANCELLING("cancelling", StreamEvent.THREAD_RUN_CANCELLING, null),
    
    /**
     * 已取消 - Run已被取消
     */
    CANCELLED("cancelled", StreamEvent.THREAD_RUN_CANCELLED, StreamEvent.THREAD_RUN_STEP_CANCELLED),
    
    /**
     * 失败 - Run执行失败
     */
    FAILED("failed", StreamEvent.THREAD_RUN_FAILED, StreamEvent.THREAD_RUN_STEP_FAILED),
    
    /**
     * 完成 - Run成功完成
     */
    COMPLETED("completed", StreamEvent.THREAD_RUN_COMPLETED, StreamEvent.THREAD_RUN_STEP_COMPLETED),
    
    /**
     * 过期 - Run因超时而过期
     */
    EXPIRED("expired", StreamEvent.THREAD_RUN_EXPIRED, StreamEvent.THREAD_RUN_STEP_EXPIRED);
    
    /**
     * 判断是否为终止状态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }

    /**
     * 判断是否停止执行
     */
    public boolean isStopExecution() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == EXPIRED || this == REQUIRES_ACTION;
    }

    /**
     * 判断是否为终止状态
     */
    public boolean isCanceled() {
        return this == CANCELLED;
    }

    private final String value;

    private final StreamEvent runStreamEvent;

    private final StreamEvent runStepStreamEvent;


    public static RunStatus fromValue(String value) {
        return Arrays.stream(RunStatus.values()).filter(runStatus -> runStatus.value.equals(value)).findAny().orElse(null);
    }
    
    /**
     * 判断是否可以执行状态转换
     */
    public boolean canTransitionTo(RunStatus target) {
        if (this == target) {
            return true;
        }
        
        // 终止状态不能转换为其他状态
        if (this.isTerminal()) {
            return false;
        }
        
        switch (this) {
            case QUEUED:
                return target == IN_PROGRESS || target == CANCELLED || target == FAILED;
            case IN_PROGRESS:
                return target == REQUIRES_ACTION || target == COMPLETED || target == FAILED || target == CANCELLING;
            case REQUIRES_ACTION:
                return target == QUEUED || target == IN_PROGRESS || target == COMPLETED || target == FAILED || target == CANCELLING || target == EXPIRED;
            case CANCELLING:
                return target == CANCELLED || target == FAILED;
            default:
                return false;
        }
    }
}
