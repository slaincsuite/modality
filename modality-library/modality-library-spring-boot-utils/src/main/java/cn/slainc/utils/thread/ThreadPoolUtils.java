package cn.slainc.utils.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThreadPoolUtils {

    private static final long DEFAULT_KEEP_ALIVE_SECONDS = 60L;

    private ThreadPoolUtils() {
    }

    public static ThreadPoolExecutor newFixedThreadPool(String threadNamePrefix, int poolSize) {
        return newFixedThreadPool(threadNamePrefix, poolSize, poolSize * 2);
    }

    public static ThreadPoolExecutor newFixedThreadPool(String threadNamePrefix, int poolSize, int queueCapacity) {
        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                namedThreadFactory(threadNamePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public static ThreadPoolExecutor newCachedThreadPool(String threadNamePrefix) {
        return new ThreadPoolExecutor(
                0,
                Integer.MAX_VALUE,
                DEFAULT_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                namedThreadFactory(threadNamePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public static ScheduledExecutorService newScheduledThreadPool(String threadNamePrefix, int poolSize) {
        return Executors.newScheduledThreadPool(poolSize, namedThreadFactory(threadNamePrefix));
    }

    public static ExecutorService newVirtualThreadPerTaskExecutor(String threadNamePrefix) {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(threadNamePrefix + "-", 0).factory());
    }

    public static ThreadFactory namedThreadFactory(String threadNamePrefix) {
        return namedThreadFactory(threadNamePrefix, false);
    }

    public static ThreadFactory namedThreadFactory(String threadNamePrefix, boolean virtual) {
        AtomicInteger counter = new AtomicInteger(1);
        String prefix = threadNamePrefix == null || threadNamePrefix.isBlank() ? "pool" : threadNamePrefix;
        if (virtual) {
            return Thread.ofVirtual().name(prefix + "-", 0).factory();
        }
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    public static void shutdown(ExecutorService executorService) {
        shutdown(executorService, 10L);
    }

    public static void shutdown(ExecutorService executorService, long awaitSeconds) {
        if (executorService == null) {
            return;
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(awaitSeconds, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static int activeCount(ThreadPoolExecutor executor) {
        return executor == null ? 0 : executor.getActiveCount();
    }

    public static long completedTaskCount(ThreadPoolExecutor executor) {
        return executor == null ? 0L : executor.getCompletedTaskCount();
    }

    public static int queueSize(ThreadPoolExecutor executor) {
        return executor == null ? 0 : executor.getQueue().size();
    }
}
