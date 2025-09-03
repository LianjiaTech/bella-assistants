package com.ke.assistant.configuration;

import com.ke.assistant.core.file.DefaultFileProvider;
import com.ke.assistant.core.file.FileProvider;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public FileProvider defaultFileProvider(OpenAiServiceFactory openAiServiceFactory) {
        return new DefaultFileProvider(openAiServiceFactory);
    }
}
