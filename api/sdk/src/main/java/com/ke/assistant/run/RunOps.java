package com.ke.assistant.run;

import lombok.Data;

import java.util.Map;

/**
 * Run 操作相关的 DTO 类
 */
public class RunOps {

    /**
     * 更新 Run 请求
     */
    @Data
    public static class UpdateRunOp {
        private Map<String, Object> metadata;
    }
}
