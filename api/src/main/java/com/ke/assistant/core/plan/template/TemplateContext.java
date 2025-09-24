package com.ke.assistant.core.plan.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.assistant.core.file.FileInfo;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Template context for Jinja template rendering
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateContext {

    private AgentInfo agent;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AgentInfo {
        private String instruction;
        private List<ToolFileInfo> files;
        private List<String> toolNames;
    }

    @Data
    @SuperBuilder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolFileInfo extends FileInfo {
        private String id;
        private String tool;
        private String name;
        private String abstractInfo;
    }
}
