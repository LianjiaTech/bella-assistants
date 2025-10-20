package com.ke.assistant.db;

import static com.ke.assistant.db.generated.Tables.ID_SEQUENCE;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;

import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ke.assistant.db.generated.tables.records.IdSequenceRecord;

import lombok.extern.slf4j.Slf4j;

/**
 * ID 生成器 生成逻辑：前缀_自增数字
 * 采用批量获取 + 内存缓存的方式提高性能
 */
@Component
@Slf4j
public class IdGenerator {

    private static final int BATCH_SIZE = 100; // 每次批量获取的ID数量
    
    // 预定义的所有ID前缀
    private static final String[] PREDEFINED_PREFIXES = {
        "asst", "msg", "thread", "run", "step", "resp"
    };
    // 内存中的ID区间缓存 prefix -> IdRange
    private final ConcurrentHashMap<String, IdRange> idRangeCache = new ConcurrentHashMap<>();
    // 每个前缀的锁，确保批量获取ID时的串行性
    private final ConcurrentHashMap<String, ReentrantLock> prefixLocks = new ConcurrentHashMap<>();
    @Autowired
    private DSLContext db;

    /**
     * 初始化ID序列表，预创建所有已知前缀的记录
     * 使用 double check 模式避免不必要的锁表操作
     */
    @PostConstruct
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public void init() {
        log.info("开始初始化ID序列表...");
        
        try {
            // 先检查所有前缀是否都已存在
            boolean needInit = false;
            for (String prefix : PREDEFINED_PREFIXES) {
                IdSequenceRecord existing = db.selectFrom(ID_SEQUENCE)
                        .where(ID_SEQUENCE.PREFIX.eq(prefix))
                        .fetchOne();
                if (existing == null) {
                    needInit = true;
                    break;
                }
            }
            
            // 如果都已存在，直接返回，避免锁表
            if (!needInit) {
                log.info("所有ID序列记录已存在，跳过初始化");
                return;
            }
            
            // 需要初始化时，锁定整个表并进行double check
            log.info("发现缺失的ID序列记录，开始锁表初始化...");
            db.selectFrom(ID_SEQUENCE).forUpdate().fetch(); // 锁表
            
            // Double check: 再次检查并创建缺失的记录
            for (String prefix : PREDEFINED_PREFIXES) {
                IdSequenceRecord existing = db.selectFrom(ID_SEQUENCE)
                        .where(ID_SEQUENCE.PREFIX.eq(prefix))
                        .fetchOne();
                        
                if (existing == null) {
                    // 插入新的前缀记录，初始值为0
                    db.insertInto(ID_SEQUENCE)
                            .set(ID_SEQUENCE.PREFIX, prefix)
                            .set(ID_SEQUENCE.CURRENT_VALUE, 0L)
                            .execute();
                    log.info("创建ID序列记录: prefix={}, initialValue=0", prefix);
                } else {
                    log.debug("ID序列记录已存在: prefix={}, currentValue={}", prefix, existing.getCurrentValue());
                }
            }
            
            log.info("ID序列表初始化完成，共处理{}个前缀", PREDEFINED_PREFIXES.length);
            
        } catch (Exception e) {
            log.error("初始化ID序列表失败", e);
            throw new RuntimeException("ID生成器初始化失败", e);
        }
    }

    /**
     * 生成递增ID
     *
     * @param prefix 前缀 (如 "asst", "msg", "run" 等)
     * @return 生成的 ID (如 "asst_1", "msg_123")
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public String generateId(String prefix) {
        // 尝试从缓存获取ID
        IdRange range = idRangeCache.get(prefix);
        if (range != null) {
            Long nextId = range.getNextId();
            if (nextId != null) {
                return prefix + "_" + nextId;
            }
        }

        // 缓存中没有可用ID，需要批量获取新的ID区间
        ReentrantLock lock = prefixLocks.computeIfAbsent(prefix, k -> new ReentrantLock());
        lock.lock();
        try {
            // 双重检查，防止并发时重复获取
            range = idRangeCache.get(prefix);
            if (range != null) {
                Long nextId = range.getNextId();
                if (nextId != null) {
                    return prefix + "_" + nextId;
                }
            }

            // 批量获取新的ID区间
            IdRange newRange = batchAcquireIdRange(prefix);
            idRangeCache.put(prefix, newRange);

            Long nextId = newRange.getNextId();
            return prefix + "_" + nextId;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 批量获取ID区间 - 使用数据库行锁保证分布式环境下的串行性
     */
    private IdRange batchAcquireIdRange(String prefix) {
        // 使用 SELECT ... FOR UPDATE 加行锁，保证分布式环境下的串行性
        IdSequenceRecord existing = db.selectFrom(ID_SEQUENCE)
                .where(ID_SEQUENCE.PREFIX.eq(prefix))
                .forUpdate() // 关键：数据库行锁
                .fetchOne();

        if (existing == null) {
            // 如果记录不存在，说明是新的前缀，动态创建
            log.warn("发现未预创建的前缀: {}, 动态创建记录", prefix);
            db.insertInto(ID_SEQUENCE)
                    .set(ID_SEQUENCE.PREFIX, prefix)
                    .set(ID_SEQUENCE.CURRENT_VALUE, (long) BATCH_SIZE)
                    .execute();
            return new IdRange(1L, BATCH_SIZE);
        }

        // 更新现有序列，批量分配
        long startId = existing.getCurrentValue() + 1;
        long endId = startId + BATCH_SIZE - 1;

        // 直接更新，因为已经通过forUpdate()锁定了记录，不需要乐观锁
        db.update(ID_SEQUENCE)
                .set(ID_SEQUENCE.CURRENT_VALUE, endId)
                .where(ID_SEQUENCE.PREFIX.eq(prefix))
                .execute();

        log.debug("批量获取ID区间: prefix={}, startId={}, endId={}", prefix, startId, endId);
        return new IdRange(startId, endId);
    }

    /**
     * 生成 Assistant ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public String generateAssistantId() {
        return generateId("asst");
    }

    /**
     * 生成 Message ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public String generateMessageId() {
        return generateId("msg");
    }

    /**
     * 生成 Thread ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public String generateThreadId() {
        return generateId("thread");
    }

    /**
     * 生成 Run ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public String generateRunId() {
        return generateId("run");
    }

    /**
     * 生成 Run Step ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public String generateRunStepId() {
        return generateId("step");
    }

    /**
     * 生成 Response ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public String generateResponseId() {
        return generateId("resp");
    }

    /**
     * ID区间缓存 - 使用AtomicLong提供更好的并发性能
     */
    private static class IdRange {
        private final AtomicLong currentId;
        private final long maxId;

        public IdRange(long startId, long maxId) {
            this.currentId = new AtomicLong(startId);
            this.maxId = maxId;
        }

        public Long getNextId() {
            long nextId = currentId.getAndIncrement();
            if (nextId > maxId) {
                return null; // 区间已用完
            }
            return nextId;
        }
    }


}
