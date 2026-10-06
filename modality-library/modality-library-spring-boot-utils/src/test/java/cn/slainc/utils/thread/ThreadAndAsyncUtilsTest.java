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

    @Test
    void daemonSingleThreadSchedulerRunsTaskWithFixedName() throws Exception {
        java.util.concurrent.ScheduledExecutorService scheduler =
                ThreadPoolUtils.newDaemonSingleThreadScheduler("test-scheduler");
        try {
            java.util.concurrent.atomic.AtomicReference<String> threadName = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            scheduler.execute(() -> {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
            });
            assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("test-scheduler", threadName.get());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void newDaemonThreadHasFixedNameAndDaemonFlag() {
        Thread thread = ThreadPoolUtils.newDaemonThread("test-worker", () -> {
        });
        assertEquals("test-worker", thread.getName());
        assertTrue(thread.isDaemon());
    }

    @Test
    void fixedNameThreadFactoryKeepsExactNameAndDaemonFlag() {
        Thread thread = ThreadPoolUtils.fixedNameThreadFactory("fixed-name").newThread(() -> {
        });
        assertEquals("fixed-name", thread.getName());
        assertTrue(thread.isDaemon());
        Thread fallback = ThreadPoolUtils.fixedNameThreadFactory(null).newThread(() -> {
        });
        assertEquals("modality-daemon", fallback.getName());
    }
}
