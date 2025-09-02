package com.ke.assistant.core.plan;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 规划器动作枚举
 * 定义规划器可以决策的动作类型
 */
@AllArgsConstructor
@Getter
public enum PlannerAction {

    /**
     * 初始化 - 用于run开启
     */
    INIT(false),

    /**
     * LLM调用 - 调用大语言模型获取响应
     */
    LLM_CALL(false),
    
    /**
     * 等待输入 - 等待用户输入或外部响应
     */
    WAIT_FOR_INPUT(true),

    /**
     * 等待内部工具执行
     */
    WAIT_FOR_TOOL(false),
    
    /**
     * 完成执行 - 标记Run执行完成
     */
    COMPLETE(true),

    /**
     * 取消执行 - 标记Run已经取消
     */
    CANCELED(true),

    /**
     * 执行错误 - 标记Run执行异常
     */
    ERROR(true),

    /**
     * 执行超时 - 标记Run执行超时
     */
    EXPIRED(true),

    ;

    public final boolean stop;

}
