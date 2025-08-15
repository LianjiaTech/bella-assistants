package com.ke.assistant.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ThreadLockService 测试类
 * 验证分布式读写锁的功能
 */
@SpringBootTest
@ActiveProfiles("ut")
public class ThreadLockServiceTest {

    @Autowired
    private ThreadLockService threadLockService;

    @Test
    public void testWriteLockBlocksReads() throws InterruptedException {
        String threadId = "write-blocks-read-" + System.currentTimeMillis();
        AtomicBoolean readExecuted = new AtomicBoolean(false);
        CountDownLatch writeLockAcquired = new CountDownLatch(1);
        CountDownLatch testCompleted = new CountDownLatch(1);

        // 启动写锁线程
        Thread writeThread = new Thread(() -> {
            threadLockService.executeWithWriteLock(threadId, () -> {
                writeLockAcquired.countDown();
                try {
                    // 等待测试完成信号
                    testCompleted.await(5, TimeUnit.SECONDS);
                    Thread.sleep(100); // 模拟工作
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });

        // 启动读锁线程，应该被写锁阻塞
        Thread readThread = new Thread(() -> {
            try {
                // 等待写锁获取
                writeLockAcquired.await(5, TimeUnit.SECONDS);
                // 尝试获取读锁，应该被阻塞
                threadLockService.executeWithReadLock(threadId, () -> {
                    readExecuted.set(true);
                }, 2, TimeUnit.SECONDS); // 短超时时间
            } catch (Exception e) {
                // 预期会因为超时而异常
            }
        });

        writeThread.start();
        readThread.start();

        // 等待读线程完成（应该因为超时失败）
        readThread.join(5000);
        
        // 检查状态
        assertTrue(threadLockService.isWriteLocked(threadId), "写锁应该被持有");
        assertFalse(readExecuted.get(), "读操作应该被阻塞");

        // 释放写锁
        testCompleted.countDown();
        writeThread.join(5000);

        // 验证锁已释放
        assertFalse(threadLockService.isLocked(threadId), "锁应该已释放");
    }

    @Test
    public void testConcurrentReadLocks() throws InterruptedException {
        String threadId = "concurrent-reads-" + System.currentTimeMillis();
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger executionCounter = new AtomicInteger(0);
        AtomicInteger maxConcurrentExecution = new AtomicInteger(0);

        // 创建多个线程同时尝试获取读锁
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await(); // 等待统一开始
                    threadLockService.executeWithReadLock(threadId, () -> {
                        int current = executionCounter.incrementAndGet();
                        maxConcurrentExecution.updateAndGet(max -> Math.max(max, current));
                        
                        try {
                            Thread.sleep(100); // 模拟工作
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        executionCounter.decrementAndGet();
                    });
                } catch (Exception e) {
                    // 忽略异常，继续测试
                } finally {
                    endLatch.countDown();
                }
            });
            thread.start();
        }

        // 统一开始
        startLatch.countDown();
        
        // 等待所有线程完成
        assertTrue(endLatch.await(30, TimeUnit.SECONDS), "所有线程应该在30秒内完成");

        // 验证多个读锁可以同时执行
        assertTrue(maxConcurrentExecution.get() > 1, "多个读锁应该能同时执行，实际并发数: " + maxConcurrentExecution.get());
    }

    @Test
    public void testLockStatusChecks() {
        String threadId = "lock-status-" + System.currentTimeMillis();
        
        // 初始状态：没有锁
        assertFalse(threadLockService.isLocked(threadId), "初始状态应该没有锁");
        assertFalse(threadLockService.isReadLocked(threadId), "初始状态应该没有读锁");
        assertFalse(threadLockService.isWriteLocked(threadId), "初始状态应该没有写锁");
    }

    @Test 
    public void testReadWriteLockInteraction() throws InterruptedException {
        String threadId = "read-write-interaction-" + System.currentTimeMillis();
        AtomicBoolean readLockStatus = new AtomicBoolean(false);
        AtomicBoolean writeLockStatus = new AtomicBoolean(false);
        CountDownLatch readLockAcquired = new CountDownLatch(1);
        CountDownLatch statusChecked = new CountDownLatch(1);

        // 启动读锁线程
        Thread readThread = new Thread(() -> {
            threadLockService.executeWithReadLock(threadId, () -> {
                readLockAcquired.countDown();
                try {
                    statusChecked.await(5, TimeUnit.SECONDS);
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });

        // 检查锁状态
        Thread checkThread = new Thread(() -> {
            try {
                readLockAcquired.await(5, TimeUnit.SECONDS);
                readLockStatus.set(threadLockService.isReadLocked(threadId));
                writeLockStatus.set(threadLockService.isWriteLocked(threadId));
                statusChecked.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        readThread.start();
        checkThread.start();

        readThread.join(10000);
        checkThread.join(10000);

        assertTrue(readLockStatus.get(), "读锁应该被检测到");
        assertFalse(writeLockStatus.get(), "写锁不应该被检测到");
    }
}