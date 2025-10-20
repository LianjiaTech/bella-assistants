package com.ke.assistant.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.ke.assistant.db.context.RepoContext;

public class TaskExecutor {
    static ThreadFactory rtf = new NamedThreadFactory("bella-runner-", false);
    static ThreadFactory etf = new NamedThreadFactory("bella-executor-", true);
    static ThreadFactory ctf = new NamedThreadFactory("bella-caller-", true);
    /**
     * 一个runner需要启动一个message executor和一个tool executor，因此是两倍的关系
     * executor的最大线程数，稍大一点，作为缓冲，因为executor的结束存在少许延迟
     * 均不设置等待队列
     */
    static ExecutorService runner = new ThreadPoolExecutor(100, 500, 10L, TimeUnit.SECONDS, new SynchronousQueue<>(), rtf);
    static ExecutorService executor = new ThreadPoolExecutor(200, 1100, 10L, TimeUnit.SECONDS, new SynchronousQueue<>(), etf);
    /**
     * 用于工具调用和工具结果发送
     * 使用公平队列：
     * 1、排队时，优先处理的一批tool大概率属于同一次run（一次run会同时加入）
     * 2、工具执行时对应工具结果发送线程一定在执行
     */
    static ExecutorService caller = new ThreadPoolExecutor(100, 1000, 10L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000, true), ctf);

    public static CompletableFuture<Void> addRunner(Runnable r) {
        return CompletableFuture.runAsync(wrapWithRepoContext(r), runner);
    }

    public static CompletableFuture<Void> addExecutor(Runnable r) {
        return CompletableFuture.runAsync(wrapWithRepoContext(r), executor);
    }

    public static CompletableFuture<Void> addToolSender(Runnable r) {
        return CompletableFuture.runAsync(wrapWithRepoContext(r), caller);
    }

    public static <T> CompletableFuture<T> supplyCaller(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(wrapWithRepoContext(supplier), caller);
    }

    /**
     * 包装 Runnable，处理 RepoContext 的传递和清理
     */
    private static Runnable wrapWithRepoContext(Runnable r) {
        // 在提交任务的线程中获取当前的 RepoContext 快照
        RepoContext.State repoContextSnapshot = RepoContext.capture();

        return () -> {
            try {
                // 在执行线程中恢复 RepoContext
                if (repoContextSnapshot != null) {
                    RepoContext.attach(repoContextSnapshot);
                }
                r.run();
            } finally {
                // 确保在执行线程结束时清理 RepoContext
                RepoContext.detach();
            }
        };
    }

    /**
     * 包装 Supplier，处理 RepoContext 的传递和清理
     */
    private static <T> Supplier<T> wrapWithRepoContext(Supplier<T> supplier) {
        // 在提交任务的线程中获取当前的 RepoContext 快照
        RepoContext.State repoContextSnapshot = RepoContext.capture();

        return () -> {
            try {
                // 在执行线程中恢复 RepoContext
                if (repoContextSnapshot != null) {
                    RepoContext.attach(repoContextSnapshot);
                }
                return supplier.get();
            } finally {
                // 确保在执行线程结束时清理 RepoContext
                RepoContext.detach();
            }
        };
    }

    public static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final boolean isDaemon;
        private final Thread.UncaughtExceptionHandler handler;

        public NamedThreadFactory(String prefix, boolean isDaemon) {
            this(prefix, isDaemon, null);
        }

        public NamedThreadFactory(String prefix, boolean isDaemon, Thread.UncaughtExceptionHandler handler) {
            this.prefix = prefix;
            this.isDaemon = isDaemon;
            this.handler = handler;
        }

        @Override
        public Thread newThread(Runnable r) {
            final Thread t = new Thread(r, String.format("%s%d", prefix, threadNumber.getAndIncrement()));
            t.setDaemon(isDaemon);
            if(this.handler != null) {
                t.setUncaughtExceptionHandler(handler);
            }
            return t;
        }
    }
}
