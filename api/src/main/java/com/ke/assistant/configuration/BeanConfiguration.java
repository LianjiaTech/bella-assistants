package com.ke.assistant.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ke.assistant.core.file.DefaultFileProvider;
import com.ke.assistant.core.file.FileProvider;
import com.ke.bella.openapi.server.OpenAiServiceFactory;

@Configuration
public class BeanConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public FileProvider defaultFileProvider(OpenAiServiceFactory openAiServiceFactory) {
        return new DefaultFileProvider(openAiServiceFactory);
    }
}
