package com.ke.assistant.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("bella.assistant")
public class AssistantProperties {
    private String keyPrefix = "bella_assistant";
    private Integer maxExecutionMinutes;
    private Integer maxExecutionSteps;
    private ToolProperties tools = new ToolProperties();
    private S3Properties s3 = new S3Properties();
}
