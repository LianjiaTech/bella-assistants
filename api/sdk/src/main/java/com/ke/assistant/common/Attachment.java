package com.ke.assistant.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Attachment 类型定义
 */
@Data
public class Attachment {

    @JsonProperty("file_id")
    private String fileId;

    private List<Tool> tools;
}
