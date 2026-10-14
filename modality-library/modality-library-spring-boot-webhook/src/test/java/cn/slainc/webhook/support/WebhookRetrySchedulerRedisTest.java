package cn.slainc.webhook.support;

import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.lock.DistributedLockTemplate;
import cn.slainc.webhook.config.WebhookProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 多节点重投去重：两个调度器抢同一把 Redisson 锁，只有持锁节点执行扫描
class WebhookRetrySchedulerRedisTest {

    private static final String SKIP_REASON = "本地 127.0.0.1:6379 没有可用的 Redis，已跳过集成测试";

    private static RedissonClient redisson;

    @BeforeAll
    static void setUpRedisson() {
        Assumptions.assumeTrue(isRedisAvailable(), SKIP_REASON);
        redisson = Redisson.create();
    }

    @AfterAll
    static void tearDownRedisson() {
        if (redisson != null) {
            redisson.shutdown(1, 5, TimeUnit.SECONDS);
        }
    }

    @Test
    void onlyTheLockHolderRunsTheRetryScan() throws Exception {
        AtomicInteger scans = new AtomicInteger();
        CountingPort port = new CountingPort(scans);
        WebhookDeliveryDispatcher dispatcher = new WebhookDeliveryDispatcher(null, new WebhookProperties(),
                port);
        WebhookProperties properties = new WebhookProperties();
        properties.setBatchSize(3);
        DistributedLockTemplate lockTemplate = new DistributedLockTemplate(redisson, new RedisProperties());
        ObjectProvider<DistributedLockTemplate> lockProvider = lockProvider(lockTemplate);
        WebhookRetryScheduler first = new WebhookRetryScheduler(dispatcher, properties, lockProvider, null);
        WebhookRetryScheduler second = new WebhookRetryScheduler(dispatcher, properties, lockProvider, null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> left = executor.submit(() -> awaitAndRetry(start, first));
            Future<?> right = executor.submit(() -> awaitAndRetry(start, second));
            start.countDown();
            left.get(10, TimeUnit.SECONDS);
            right.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(scans.get()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<DistributedLockTemplate> lockProvider(DistributedLockTemplate lockTemplate) {
        ObjectProvider<DistributedLockTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(lockTemplate);
        return provider;
    }

    private static void awaitAndRetry(CountDownLatch start, WebhookRetryScheduler scheduler) {
        try {
            start.await(5, TimeUnit.SECONDS);
            scheduler.retryDue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class CountingPort implements cn.slainc.webhook.spi.WebhookDeliveryPort {

        private final AtomicInteger scans;

        private CountingPort(AtomicInteger scans) {
            this.scans = scans;
        }

        @Override
        public Long createPending(cn.slainc.webhook.spi.WebhookDeliveryCommand command) {
            return null;
        }

        @Override
        public List<cn.slainc.webhook.spi.WebhookDeliveryTask> findDue(int limit) {
            scans.incrementAndGet();
            return List.of();
        }

        @Override
        public void markSuccess(Long id, int httpStatus, String responseBody, long costMillis) {
        }

        @Override
        public void markFailure(Long id, int attempt, int httpStatus, String responseBody,
                                String errorMessage, java.time.LocalDateTime nextRetryAt, boolean exhausted,
                                long costMillis) {
        }
    }

    private static boolean isRedisAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 6379), 500);
            socket.setSoTimeout(800);
            OutputStream out = socket.getOutputStream();
            out.write("PING".getBytes(StandardCharsets.UTF_8));
            out.write(13);
            out.write(10);
            out.flush();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[64];
            int read = in.read(buffer);
            return read > 0 && new String(buffer, 0, read, StandardCharsets.UTF_8).contains("PONG");
        } catch (Exception exception) {
            return false;
        }
    }
}
