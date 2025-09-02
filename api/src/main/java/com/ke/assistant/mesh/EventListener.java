package com.ke.assistant.mesh;

/**
 * 事件监听器接口
 */
@FunctionalInterface
public interface EventListener {
    
    /**
     * 处理接收到的事件
     * @param event 事件对象
     */
    void onEvent(Event event);
}