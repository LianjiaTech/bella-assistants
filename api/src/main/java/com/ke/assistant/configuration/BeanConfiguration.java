package com.ke.assistant.configuration;

import com.ke.assistant.core.file.DefaultFileProvider;
import com.ke.assistant.core.file.FileProvider;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public FileProvider defaultFileProvider(OpenAiService openAiService) {
        return new DefaultFileProvider(openAiService);
    }
}
