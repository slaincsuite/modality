package cn.slainc.redis.lock;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistributedLockTemplateTest {

    private static RedissonClient redissonClient;

    private static DistributedLockTemplate template;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + RedisTestSupport.HOST + ":" + RedisTestSupport.PORT)
                .setPassword(RedisTestSupport.PASSWORD)
                .setDatabase(0);
        redissonClient = Redisson.create(config);
        RedisProperties properties = new RedisProperties();
        properties.getLock().setWaitTime(1L);
        properties.getLock().setLeaseTime(20L);
        template = new DistributedLockTemplate(redissonClient, properties);
    }

    @AfterAll
    static void afterAll() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void 可重入锁在同一线程内可重复获取() {
        String key = "modality:test:lock:" + UUID.randomUUID();
        try (LockHandle first = template.acquire(key, 2L, 10L, LockType.REENTRANT);
             LockHandle second = template.acquire(key, 2L, 10L, LockType.REENTRANT)) {
            assertThat(first.isLocked()).isTrue();
            assertThat(second.isLocked()).isTrue();
            assertThat(template.getLock(key).getHoldCount()).isEqualTo(2);
        }
        assertThat(template.isLocked(key)).isFalse();
    }

    @Test
    void 互斥锁在其它线程占用时获取失败并可降级() throws Exception {
        String key = "modality:test:lock:" + UUID.randomUUID();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> holder = pool.submit((java.util.concurrent.Callable<Boolean>) () -> {
                try (LockHandle handle = template.acquire(key, 2L, 10L, LockType.REENTRANT)) {
                    Thread.sleep(2500L);
                    return handle.isLocked();
                }
            });
            Thread.sleep(200L);

            try (LockHandle handle = template.acquire(key, 1L, 10L, LockType.REENTRANT)) {
                assertThat(handle.isLocked()).isFalse();
            }
            assertThat(holder.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 执行器在获取失败时抛出中文异常() throws Exception {
        String key = "modality:test:lock:" + UUID.randomUUID();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (LockHandle lockHandle = template.acquire(key, 2L, 10L, LockType.REENTRANT)) {
            assertThat(lockHandle.isLocked()).isTrue();
            Future<String> other = pool.submit((java.util.concurrent.Callable<String>) () ->
                    template.executeWithLock(key, 1L, 10L, () -> "不应返回"));
            assertThatThrownBy(() -> other.get(15, TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasRootCauseInstanceOf(LockAcquireException.class)
                    .hasMessageContaining("获取分布式锁失败");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 执行器返回业务结果并统计加锁指标() {
        String key = "modality:test:lock:" + UUID.randomUUID();
        long before = template.getStats().getAcquireSuccess();
        String value = template.executeWithLock(key, 2L, 10L, () -> "业务结果");
        assertThat(value).isEqualTo("业务结果");
        assertThat(template.getStats().getAcquireSuccess()).isGreaterThan(before);
        assertThat(template.getStats().toString()).contains("锁统计");
    }

    @Test
    void 读写锁支持并发读与独占写() {
        String key = "modality:test:lock:" + UUID.randomUUID();
        try (LockHandle read = template.acquire(key, 2L, 10L, LockType.READ);
             LockHandle readAgain = template.acquire(key, 2L, 10L, LockType.READ)) {
            assertThat(read.isLocked()).isTrue();
            assertThat(readAgain.isLocked()).isTrue();
        }
        try (LockHandle write = template.acquire(key, 2L, 10L, LockType.WRITE)) {
            assertThat(write.isLocked()).isTrue();
        }
        assertThat(template.executeWithReadLock(key, 2L, 10L, () -> "读锁结果")).isEqualTo("读锁结果");
        assertThat(template.executeWithWriteLock(key, 2L, 10L, () -> "写锁结果")).isEqualTo("写锁结果");
    }

    @Test
    void 信号量倒计时锁与原子长整型封装可用() throws Exception {
        String name = "modality:test:lock:semaphore:" + UUID.randomUUID();
        assertThat(template.getSemaphore(name).trySetPermits(2)).isTrue();
        assertThat(template.getSemaphore(name).tryAcquire(2)).isTrue();
        assertThat(template.getSemaphore(name).availablePermits()).isZero();

        String latchName = "modality:test:lock:latch:" + UUID.randomUUID();
        assertThat(template.getCountDownLatch(latchName).trySetCount(2L)).isTrue();
        template.getCountDownLatch(latchName).countDown();
        template.getCountDownLatch(latchName).countDown();
        assertThat(template.getCountDownLatch(latchName).await(2, TimeUnit.SECONDS)).isTrue();

        String atomicName = "modality:test:lock:atomic:" + UUID.randomUUID();
        assertThat(template.getAtomicLong(atomicName).incrementAndGet()).isEqualTo(1L);
        assertThat(template.getAtomicLong(atomicName).addAndGet(4L)).isEqualTo(5L);
    }

    @Test
    void 看门狗模式与锁键为空校验() {
        String key = "modality:test:lock:" + UUID.randomUUID();
        AtomicBoolean executed = new AtomicBoolean();
        template.executeWithLock(key, 2L, -1L, () -> {
            executed.set(true);
            return "看门狗";
        });
        assertThat(executed).isTrue();
        assertThat(template.getStats().getWatchdogRenewals()).isPositive();

        assertThatThrownBy(() -> template.acquire("  ", LockType.REENTRANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分布式锁的键不能为空");
    }
}
