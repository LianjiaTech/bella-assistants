package com.ke.assistant;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class TaskExecutor {
    static ThreadFactory tf = new NamedThreadFactory("bella-worker-", true);
    static ScheduledExecutorService executor = Executors.newScheduledThreadPool(1000, tf);

    public static CompletableFuture<Void> submit(Runnable r) {
        return CompletableFuture.runAsync(r, executor);
    }

    public static <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
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
