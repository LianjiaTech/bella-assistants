package com.ke.assistant.core.plan.template;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.assistant.core.run.FileInfo;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Template context for Jinja template rendering
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateContext {
    
    private EnvironmentInfo env;
    private UserInfo user;
    private AgentInfo agent;
    private List<ProfileInfo> profile;
    
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EnvironmentInfo {
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime datetime;
    }
    
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserInfo {
        private String gender;
        private Integer age;
        private String job;
        private Map<String, String> others;
    }
    
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
    
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProfileInfo {
        private String content;
        private Integer index;
    }
}
