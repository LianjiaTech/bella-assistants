package com.ke.assistant.mesh.impl;

import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.mesh.Event;
import com.ke.assistant.mesh.EventListener;
import com.ke.assistant.mesh.ServiceMesh;
import com.ke.bella.openapi.server.BellaServerContextHolder;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RSetCache;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Redis Mesh实现类
 */
@Slf4j
public class RedisMesh implements ServiceMesh {

    private final RedissonClient redissonClient;

    private final AssistantProperties assistantProperties;
    
    private String instanceId;
    
    // Redis Key常量
    private static final String RUNNING_RUN_MAPPING = "assistant:running-runs";
    private static final String INSTANCE_REGISTRY = "assistant:instances";
    private static final String BROADCAST_TOPIC = "assistant:broadcast";
    private static final String PRIVATE_TOPIC_PREFIX = "assistant:private:";
    
    // 事件监听器映射
    private final Map<String, EventListener> eventListeners = new ConcurrentHashMap<>();
    
    // 心跳调度器
    private ScheduledExecutorService heartbeatScheduler;
    
    // Redis订阅对象
    private RTopic broadcastTopic;
    private RTopic privateTopic;

    public RedisMesh(RedissonClient redissonClient, AssistantProperties assistantProperties) {
        this.redissonClient = redissonClient;
        this.assistantProperties = assistantProperties;

    }

    @PostConstruct
    public void init() {
        // 生成实例ID
        this.instanceId = generateInstanceId();
        log.info("Redis Mesh initialized with instance ID: {}", instanceId);
    }
    
    @Override
    public String getInstanceId() {
        return instanceId;
    }
    
    private String generateInstanceId() {
        String ip = BellaServerContextHolder.getIp();
        int port = BellaServerContextHolder.getPort();
        String hostname = ip + ":" + port;
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return hostname + "-" + uuid;
    }
    
    @Override
    public void registerListener(String eventName, EventListener listener) {
        eventListeners.put(eventName, listener);
        log.debug("Registered event listener for: {}", eventName);
    }
    
    @Override
    public void removeListener(String eventName) {
        eventListeners.remove(eventName);
        log.debug("Removed event listener for: {}", eventName);
    }
    
    @Override
    public void sendBroadcastMessage(Event event) {
        event.setSourceInstanceId(instanceId);
        String eventJson = JacksonUtils.serialize(event);
        Assert.notNull(eventJson, "Event can not be null");
        broadcastTopic.publish(eventJson);
    }
    
    @Override
    public void sendPrivateMessage(String targetInstanceId, Event event) {
        event.setSourceInstanceId(instanceId);
        event.setTargetInstanceId(targetInstanceId);
        String eventJson = JacksonUtils.serialize(event);
        Assert.notNull(eventJson, "Event can not be null");
        RTopic targetTopic = redissonClient.getTopic(PRIVATE_TOPIC_PREFIX + targetInstanceId);
        targetTopic.publish(eventJson);
    }
    
    @Override
    public void addRunningRun(String runId, int timeoutSeconds) {
        RMapCache<String, String> runningRuns = redissonClient.getMapCache(buildKey(RUNNING_RUN_MAPPING));
        runningRuns.put(runId, instanceId, timeoutSeconds, TimeUnit.SECONDS);
        log.debug("Added running run mapping: {} -> {} (timeout: {}s)", runId, instanceId, timeoutSeconds);
    }
    
    @Override
    public void removeRunningRun(String runId) {
        RMapCache<String, String> runningRuns = redissonClient.getMapCache(buildKey(RUNNING_RUN_MAPPING));
        runningRuns.remove(runId);
        log.debug("Removed running run mapping: {}", runId);
    }
    
    @Override
    public String getRunningRunInstanceId(String runId) {
        RMapCache<String, String> runningRuns = redissonClient.getMapCache(buildKey(RUNNING_RUN_MAPPING));
        return runningRuns.get(runId);
    }
    
    @Override
    public Set<String> getOnlineInstances() {
        RSetCache<String> instances = redissonClient.getSetCache(buildKey(INSTANCE_REGISTRY));
        return instances.readAll();
    }
    
    @Override
    public boolean isInstanceOnline(String instanceId) {
        RSetCache<String> instances = redissonClient.getSetCache(buildKey(INSTANCE_REGISTRY));
        return instances.contains(instanceId);
    }
    
    @Override
    public void start() {
        try {
            // 注册到实例注册表
            registerInstance();
            
            // 初始化消息订阅
            initializeTopics();
            
            // 启动心跳调度器
            startHeartbeat();
            
            log.info("Redis Mesh started successfully for instance: {}", instanceId);
        } catch (Exception e) {
            log.error("Failed to start Redis Mesh", e);
            throw new RuntimeException("Failed to start Redis Mesh", e);
        }
    }
    
    @Override
    @PreDestroy
    public void stop() {
        try {
            // 停止心跳
            if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
                heartbeatScheduler.shutdown();
            }
            
            // 注销实例
            unregisterInstance();
            
            log.info("Redis Mesh stopped successfully for instance: {}", instanceId);
        } catch (Exception e) {
            log.error("Error during Redis Mesh shutdown", e);
        }
    }
    
    private void registerInstance() {
        RSetCache<String> instances = redissonClient.getSetCache(buildKey(INSTANCE_REGISTRY));
        instances.add(instanceId, 60, TimeUnit.SECONDS); // 60秒过期
    }
    
    private void unregisterInstance() {
        RSetCache<String> instances = redissonClient.getSetCache(buildKey(INSTANCE_REGISTRY));
        instances.remove(instanceId);
        log.debug("Unregistered instance: {}", instanceId);
    }
    
    private void initializeTopics() {
        // 初始化广播主题
        broadcastTopic = redissonClient.getTopic(buildKey(BROADCAST_TOPIC));
        broadcastTopic.addListener(String.class, (channel, eventJson) -> handleIncomingMessage(eventJson, false));
        
        // 初始化私有主题
        privateTopic = redissonClient.getTopic(buildKey(PRIVATE_TOPIC_PREFIX + instanceId));
        privateTopic.addListener(String.class, (channel, eventJson) -> handleIncomingMessage(eventJson, true));
        
        log.debug("Initialized message topics for instance: {}", instanceId);
    }
    
    private void handleIncomingMessage(String eventJson, boolean isPrivate) {
        Event event = JacksonUtils.deserialize(eventJson, Event.class);


        if(event == null) {
            return;
        }

        // 忽略自己发送的消息
        if (instanceId.equals(event.getSourceInstanceId())) {
            return;
        }

        log.debug("Received {} message: {} from {}",
                isPrivate ? "private" : "broadcast",
                event.getName(),
                event.getSourceInstanceId());

        // 查找并执行监听器
        EventListener listener = eventListeners.get(event.getName());
        if (listener != null) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.error("Error processing event: {} from {}", event.getName(), event.getSourceInstanceId(), e);
            }
        } else {
            log.debug("No listener registered for event: {}", event.getName());
        }
    }
    
    private void startHeartbeat() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "redis-mesh-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeat, 30, 30, TimeUnit.SECONDS);
    }
    
    private void sendHeartbeat() {
        try {
            // 更新实例注册表中的过期时间
            registerInstance();
        } catch (Exception e) {
            log.error("Error sending heartbeat", e);
        }
    }
    
    private String buildKey(String key) {
        return assistantProperties.getKeyPrefix() + ":" + key;
    }
}
