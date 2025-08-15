package com.ke.assistant.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("bella.assistant")
public class AssistantProperties {
    public String idPrefix = "bella_assistant";
}
