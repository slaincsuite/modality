package cn.slainc.utils.thread;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class AsyncUtils {

    private AsyncUtils() {
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier);
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, ExecutorService executor) {
        return executor == null ? CompletableFuture.supplyAsync(supplier) : CompletableFuture.supplyAsync(supplier, executor);
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable);
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable, ExecutorService executor) {
        return executor == null ? CompletableFuture.runAsync(runnable) : CompletableFuture.runAsync(runnable, executor);
    }

    public static <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> futures) {
        if (futures == null || futures.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    List<T> results = new ArrayList<>(futures.size());
                    for (CompletableFuture<T> future : futures) {
                        results.add(future.join());
                    }
                    return results;
                });
    }

    public static <T> T join(CompletableFuture<T> future) {
        return future == null ? null : future.join();
    }

    public static <T> T get(CompletableFuture<T> future, Duration timeout) {
        if (future == null) {
            return null;
        }
        Duration actual = timeout == null ? Duration.ofSeconds(30) : timeout;
        try {
            return future.get(actual.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("异步任务被中断", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("异步任务执行失败", ex.getCause());
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new IllegalStateException("异步任务超时（" + actual + "）", ex);
        }
    }

    public static <T> T getOrDefault(CompletableFuture<T> future, Duration timeout, T defaultValue) {
        try {
            return get(future, timeout);
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    public static <T> CompletableFuture<T> exceptionally(CompletableFuture<T> future, T defaultValue) {
        return future == null ? null : future.exceptionally(ex -> defaultValue);
    }

    public static ScheduledExecutorService schedule(Runnable task, Duration initialDelay, Duration period,
                                                    ExecutorService executor) {
        ScheduledExecutorService scheduler = ThreadPoolUtils.newScheduledThreadPool("scheduler", 1);
        scheduler.scheduleAtFixedRate(task, initialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
        return scheduler;
    }
}
