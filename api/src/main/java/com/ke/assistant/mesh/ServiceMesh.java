package com.ke.assistant.mesh;

import java.util.Set;

/**
 * Service Mesh核心接口
 * 提供跨实例通信和状态管理功能
 * 支持多种实现方式（如Redis、Kafka、RabbitMQ等）
 */
public interface ServiceMesh {
    
    /**
     * 获取当前实例ID
     */
    String getInstanceId();
    
    /**
     * 注册事件监听器
     * @param eventName 事件名称
     * @param listener 监听器
     */
    void registerListener(String eventName, EventListener listener);
    
    /**
     * 移除事件监听器
     * @param eventName 事件名称
     */
    void removeListener(String eventName);
    
    /**
     * 发送广播消息
     * @param event 事件对象
     */
    void sendBroadcastMessage(Event event);
    
    /**
     * 发送私有消息给指定实例
     * @param instanceId 目标实例ID
     * @param event 事件对象
     */
    void sendPrivateMessage(String instanceId, Event event);
    
    /**
     * 添加运行中的Run映射
     * @param runId Run ID
     * @param timeoutSeconds 超时时间（秒）
     */
    void addRunningRun(String runId, int timeoutSeconds);
    
    /**
     * 移除运行中的Run映射
     * @param runId Run ID
     */
    void removeRunningRun(String runId);
    
    /**
     * 获取运行中Run的实例ID
     * @param runId Run ID
     * @return 实例ID，如果不存在返回null
     */
    String getRunningRunInstanceId(String runId);
    
    /**
     * 获取所有在线实例ID
     * @return 实例ID集合
     */
    Set<String> getOnlineInstances();
    
    /**
     * 检查实例是否在线
     * @param instanceId 实例ID
     * @return 是否在线
     */
    boolean isInstanceOnline(String instanceId);
    
    /**
     * 启动Mesh服务
     */
    void start();
    
    /**
     * 停止Mesh服务
     */
    void stop();
}
