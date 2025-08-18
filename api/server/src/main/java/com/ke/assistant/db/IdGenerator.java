package com.ke.assistant.db;

import com.ke.assistant.configuration.AssistantProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;

/**
 * ID 生成器 生成逻辑：前缀_自增数字
 */
@Component
@Slf4j
public class IdGenerator {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private Environment environment;

    @Autowired
    private AssistantProperties assistantProperties;

    private String idPrefix;

    private boolean isProdProfile;

    @PostConstruct
    public void init() {
        String[] activeProfiles = environment.getActiveProfiles();
        this.isProdProfile = Arrays.asList(activeProfiles).contains("prod");
        this.idPrefix = assistantProperties.getKeyPrefix();
    }

    private String getIdKey(String prefix) {
        return String.format("%s_%s_id", idPrefix, prefix);
    }

    /**
     * 生成 ID（不使用锁）
     *
     * @param prefix 前缀 (如 "asst", "msg", "run" 等)
     *
     * @return 生成的 ID (如 "asst_1", "msg_123")
     */
    public String generateId(String prefix) {
        String key = getIdKey(prefix);
        Long id = redisTemplate.opsForValue().increment(key, 1);

        // 处理测试环境
        if(!isProdProfile && "run".equals(prefix)) {
            return prefix + "_" + id + "_test";
        }

        return prefix + "_" + id;
    }

    /**
     * 生成 Assistant ID
     */
    public String generateAssistantId() {
        return generateId("asst");
    }

    /**
     * 生成 Message ID
     */
    public String generateMessageId() {
        return generateId("msg");
    }

    /**
     * 生成 Thread ID
     */
    public String generateThreadId() {
        return generateId("thread");
    }

    /**
     * 生成 Run ID
     */
    public String generateRunId() {
        return generateId("run");
    }

    /**
     * 生成 Run Step ID
     */
    public String generateRunStepId() {
        return generateId("step");
    }

    /**
     * 生成 Response ID
     */
    public String generateResponseId() {
        return generateId("resp");
    }

}
