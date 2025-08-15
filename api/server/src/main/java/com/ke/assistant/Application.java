package com.ke.assistant;

import com.ke.assistant.configuration.AssistantProperties;
import com.ke.bella.openapi.server.BellaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bella Assistant Application
 */
@EnableConfigurationProperties(AssistantProperties.class)
@ComponentScan(basePackages = {"com.ke.assistant"})
@BellaService
@SpringBootApplication
@EnableScheduling
@Slf4j
public class Application {
    
    public static void main(String[] args) {
        try {
            log.info("Starting Bella Assistant application...");
            SpringApplication.run(Application.class, args);
            log.info("Bella Assistant application started successfully!");
        } catch (Exception e) {
            log.error("Failed to start Bella Assistant application", e);
            throw e;
        }
    }
}