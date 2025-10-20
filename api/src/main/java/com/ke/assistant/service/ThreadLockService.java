package com.ke.assistant.service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 线程级别的分布式读写锁服务
 * - 单条消息插入使用读锁，允许并发执行
 * - 批量操作使用写锁，确保顺序性和原子性
 */
@Service
@Slf4j
public class ThreadLockService {

    private static final String THREAD_LOCK_KEY_PREFIX = "thread:message:lock:";
    @Autowired
    private RedissonClient redissonClient;

    /**
     * 获取指定线程的读写锁
     */
    private RReadWriteLock getReadWriteLock(String threadId) {
        return redissonClient.getReadWriteLock(THREAD_LOCK_KEY_PREFIX + threadId);
    }

    /**
     * 执行带读锁的操作（适用于单条消息插入，允许并发）
     * @param threadId 线程ID
     * @param operation 要执行的操作
     */
    public void executeWithReadLock(String threadId, Runnable operation) {
        executeWithReadLock(threadId, operation, 10, TimeUnit.SECONDS);
    }

    /**
     * 执行带读锁的操作并返回结果
     * @param threadId 线程ID
     * @param supplier 要执行的操作
     * @return 操作结果
     */
    public <T> T executeWithReadLock(String threadId, Supplier<T> supplier) {
        return executeWithReadLock(threadId, supplier, 10, TimeUnit.SECONDS);
    }

    /**
     * 执行带读锁的操作
     * @param threadId 线程ID
     * @param operation 要执行的操作
     * @param timeout 锁超时时间
     * @param timeUnit 时间单位
     */
    public void executeWithReadLock(String threadId, Runnable operation, long timeout, TimeUnit timeUnit) {
        RReadWriteLock readWriteLock = getReadWriteLock(threadId);
        try {
            if (readWriteLock.readLock().tryLock(timeout, timeUnit)) {
                try {
                    log.debug("Acquired read lock for thread: {}", threadId);
                    operation.run();
                } finally {
                    readWriteLock.readLock().unlock();
                    log.debug("Released read lock for thread: {}", threadId);
                }
            } else {
                throw new RuntimeException("Failed to acquire read lock for thread: " + threadId + " within " + timeout + " " + timeUnit);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for read lock for thread: " + threadId, e);
        }
    }

    /**
     * 执行带读锁的操作并返回结果
     * @param threadId 线程ID
     * @param supplier 要执行的操作
     * @param timeout 锁超时时间
     * @param timeUnit 时间单位
     * @return 操作结果
     */
    public <T> T executeWithReadLock(String threadId, Supplier<T> supplier, long timeout, TimeUnit timeUnit) {
        RReadWriteLock readWriteLock = getReadWriteLock(threadId);
        try {
            if (readWriteLock.readLock().tryLock(timeout, timeUnit)) {
                try {
                    log.debug("Acquired read lock for thread: {}", threadId);
                    return supplier.get();
                } finally {
                    readWriteLock.readLock().unlock();
                    log.debug("Released read lock for thread: {}", threadId);
                }
            } else {
                throw new RuntimeException("Failed to acquire read lock for thread: " + threadId + " within " + timeout + " " + timeUnit);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for read lock for thread: " + threadId, e);
        }
    }

    /**
     * 执行带写锁的操作（适用于批量操作，确保顺序性）
     * @param threadId 线程ID
     * @param operation 要执行的操作
     */
    public void executeWithWriteLock(String threadId, Runnable operation) {
        executeWithWriteLock(threadId, operation, 10, TimeUnit.SECONDS);
    }

    /**
     * 执行带写锁的操作并返回结果
     * @param threadId 线程ID
     * @param supplier 要执行的操作
     * @return 操作结果
     */
    public <T> T executeWithWriteLock(String threadId, Supplier<T> supplier) {
        return executeWithWriteLock(threadId, supplier, 10, TimeUnit.SECONDS);
    }

    /**
     * 执行带写锁的操作
     * @param threadId 线程ID
     * @param operation 要执行的操作
     * @param timeout 锁超时时间
     * @param timeUnit 时间单位
     */
    public void executeWithWriteLock(String threadId, Runnable operation, long timeout, TimeUnit timeUnit) {
        RReadWriteLock readWriteLock = getReadWriteLock(threadId);
        try {
            if (readWriteLock.writeLock().tryLock(timeout, timeUnit)) {
                try {
                    log.debug("Acquired write lock for thread: {}", threadId);
                    operation.run();
                } finally {
                    readWriteLock.writeLock().unlock();
                    log.debug("Released write lock for thread: {}", threadId);
                }
            } else {
                throw new RuntimeException("Failed to acquire write lock for thread: " + threadId + " within " + timeout + " " + timeUnit);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for write lock for thread: " + threadId, e);
        }
    }

    /**
     * 执行带写锁的操作并返回结果
     * @param threadId 线程ID
     * @param supplier 要执行的操作
     * @param timeout 锁超时时间
     * @param timeUnit 时间单位
     * @return 操作结果
     */
    public <T> T executeWithWriteLock(String threadId, Supplier<T> supplier, long timeout, TimeUnit timeUnit) {
        RReadWriteLock readWriteLock = getReadWriteLock(threadId);
        try {
            if (readWriteLock.writeLock().tryLock(timeout, timeUnit)) {
                try {
                    log.debug("Acquired write lock for thread: {}", threadId);
                    return supplier.get();
                } finally {
                    readWriteLock.writeLock().unlock();
                    log.debug("Released write lock for thread: {}", threadId);
                }
            } else {
                throw new RuntimeException("Failed to acquire write lock for thread: " + threadId + " within " + timeout + " " + timeUnit);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for write lock for thread: " + threadId, e);
        }
    }

    /**
     * 检查指定线程是否被写锁定
     * @param threadId 线程ID
     * @return 是否被写锁定
     */
    public boolean isWriteLocked(String threadId) {
        try {
            RReadWriteLock readWriteLock = getReadWriteLock(threadId);
            return readWriteLock.writeLock().isLocked();
        } catch (Exception e) {
            log.debug("Error checking write lock status for thread: {}", threadId, e);
            return false;
        }
    }

    /**
     * 检查指定线程是否被读锁定
     * @param threadId 线程ID
     * @return 是否被读锁定
     */
    public boolean isReadLocked(String threadId) {
        try {
            RReadWriteLock readWriteLock = getReadWriteLock(threadId);
            return readWriteLock.readLock().isLocked();
        } catch (Exception e) {
            log.debug("Error checking read lock status for thread: {}", threadId, e);
            return false;
        }
    }

    /**
     * 检查指定线程是否被锁定（读锁或写锁）
     * @param threadId 线程ID
     * @return 是否被锁定
     */
    public boolean isLocked(String threadId) {
        return isReadLocked(threadId) || isWriteLocked(threadId);
    }
}
