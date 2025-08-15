package com.ke.assistant.thread;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.assistant.message.MessageOps;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Thread 操作相关的 DTO 类
 */
public class ThreadOps {

    /**
     * 创建 Thread 请求
     */
    @Data
    public static class CreateThreadOp {

        private String object = "thread";

        private String user = "";

        private List<MessageOps.CreateMessageOp> messages;

        @JsonProperty("tool_resources")
        private Map<String, Object> toolResources;

        private Map<String, Object> metadata;

        private Map<String, Object> environment;
    }

    /**
     * 更新 Thread 请求
     */
    @Data
    public static class UpdateThreadOp {

        @JsonProperty("tool_resources")
        private Map<String, Object> toolResources;

        private Map<String, Object> metadata;

        private Map<String, Object> environment;
    }
}
