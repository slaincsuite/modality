package cn.slainc.utils.thread;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadAndAsyncUtilsTest {

    @Test
    void fixedPoolExecutesTasks() {
        ThreadPoolExecutor executor = ThreadPoolUtils.newFixedThreadPool("test", 2, 100);
        try {
            AtomicInteger counter = new AtomicInteger();
            for (int i = 0; i < 10; i++) {
                executor.execute(counter::incrementAndGet);
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(10, counter.get());
            assertEquals(10L, ThreadPoolUtils.completedTaskCount(executor));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        } finally {
            ThreadPoolUtils.shutdown(executor);
        }
    }

    @Test
    void virtualThreadExecutor() {
        ExecutorService executor = ThreadPoolUtils.newVirtualThreadPerTaskExecutor("vt");
        try {
            CompletableFuture<String> future = AsyncUtils.supplyAsync(() -> "virtual", executor);
            assertEquals("virtual", AsyncUtils.get(future, Duration.ofSeconds(5)));
        } finally {
            ThreadPoolUtils.shutdown(executor);
        }
    }

    @Test
    void asyncHelpers() {
        assertEquals("value", AsyncUtils.join(AsyncUtils.supplyAsync(() -> "value")));
        CompletableFuture<String> failed = AsyncUtils.supplyAsync(() -> {
            throw new IllegalStateException("模拟失败");
        });
        assertEquals("fallback", AsyncUtils.getOrDefault(failed, Duration.ofSeconds(2), "fallback"));
        assertEquals("default", AsyncUtils.join(AsyncUtils.exceptionally(failed, "default")));

        List<CompletableFuture<Integer>> futures = List.of(
                AsyncUtils.supplyAsync(() -> 1),
                AsyncUtils.supplyAsync(() -> 2));
        assertEquals(List.of(1, 2), AsyncUtils.join(AsyncUtils.allOf(futures)));
        assertEquals(List.of(), AsyncUtils.join(AsyncUtils.allOf(List.of())));
        assertTrue(AsyncUtils.join(AsyncUtils.runAsync(() -> {
        })) == null);
    }

    @Test
    void timeoutPropagates() {
        CompletableFuture<String> slow = AsyncUtils.supplyAsync(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return "late";
        });
        assertThrows(IllegalStateException.class, () -> AsyncUtils.get(slow, Duration.ofMillis(50)));
    }

    @Test
    void namedThreadFactoryPrefixesThreads() {
        Thread thread = ThreadPoolUtils.namedThreadFactory("worker").newThread(() -> {
        });
        assertTrue(thread.getName().startsWith("worker-"));
        assertTrue(thread.isDaemon());
        Thread virtual = ThreadPoolUtils.namedThreadFactory("v", true).newThread(() -> {
        });
        assertTrue(virtual.isVirtual());
    }
}
