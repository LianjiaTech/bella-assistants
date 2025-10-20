package com.ke.assistant.mesh;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis Mesh事件定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    
    /**
     * 事件名称
     */
    private String name;
    
    /**
     * 事件载荷
     */
    private String payload;
    
    /**
     * 事件元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 事件创建时间
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * 源实例ID
     */
    private String sourceInstanceId;
    
    /**
     * 目标实例ID（用于私有消息）
     */
    private String targetInstanceId;


    public static Event cancelEvent(String runId) {
        return Event.builder()
                .name(EventConstants.EVENT_CANCEL_RUN)
                .payload(runId)
                .build();
    }
}
