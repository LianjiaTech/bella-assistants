package com.ke.assistant.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用删除响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteResponse {
    private String id;
    private String object;
    private boolean deleted;
}