package com.ke.assistant.configuration;

import com.google.common.collect.Lists;
import com.ke.assistant.core.run.FileProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public FileProvider defaultFileProvider() {
        return fileIds -> Lists.newArrayList();
    }
}
