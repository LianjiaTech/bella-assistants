package com.ke.assistant.mesh;

/**
 * 事件常量定义
 */
public final class EventConstants {
    
    private EventConstants() {}
    
    /**
     * Run恢复事件
     */
    public static final String EVENT_RESUME_RUN = "resumeAssistantRun";
    
    /**
     * Run取消事件
     */
    public static final String EVENT_CANCEL_RUN = "cancelAssistantRun";
    
    /**
     * 心跳事件
     */
    public static final String EVENT_HEARTBEAT = "heartbeat";
}
