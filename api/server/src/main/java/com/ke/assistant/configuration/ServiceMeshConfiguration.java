package com.ke.assistant.configuration;

import com.ke.assistant.mesh.ServiceMesh;
import com.ke.assistant.mesh.impl.RedisMesh;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Service Mesh配置类
 * 负责启动和管理Service Mesh服务
 */
@Configuration
@Slf4j
public class ServiceMeshConfiguration implements ApplicationRunner {
    
    @Autowired
    @Lazy
    private ServiceMesh serviceMesh;

    @Bean
    @ConditionalOnMissingBean
    public ServiceMesh serviceMesh(RedissonClient redissonClient, AssistantProperties assistantProperties) {
        return new RedisMesh(redissonClient, assistantProperties);
    }
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Starting Service Mesh service...");
        serviceMesh.start();
        log.info("Service Mesh service started successfully");
    }
}
