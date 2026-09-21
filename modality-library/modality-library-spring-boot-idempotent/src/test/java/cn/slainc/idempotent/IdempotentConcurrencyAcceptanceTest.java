package cn.slainc.idempotent;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.constant.CacheKeys;
import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import cn.slainc.idempotent.exception.DuplicateSubmitException;
import cn.slainc.idempotent.exception.IdempotentRetryExceededException;
import cn.slainc.idempotent.metrics.IdempotentMetrics;
import cn.slainc.idempotent.store.IdempotentStore;
import cn.slainc.idempotent.token.IdempotentToken;
import cn.slainc.idempotent.token.IdempotentTokenService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = IdempotentTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotentConcurrencyAcceptanceTest {

    @Autowired
    private IdempotentTestApplication.OrderService orderService;

    @Autowired
    private IdempotentTestApplication.TokenOrderService tokenOrderService;

    @Autowired
    private IdempotentTestApplication.RetryOrderService retryOrderService;

    @Autowired
    private IdempotentTokenService tokenService;

    @Autowired
    private IdempotentStore idempotentStore;

    @Autowired
    private IdempotentMetrics metrics;

    @Autowired
    private DynamicConfigService dynamicConfigService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeAll
    static void requireRedis() {
        IdempotentRedisTestSupport.requireRedis();
    }

    @Test
    void concurrentSubmitExecutesOnceOthersGetDuplicateHint() throws Exception {
        assertThat(idempotentStore.name()).as("真实上下文应选中 Redis 存储").isEqualTo("redis");
        orderService.resetBarrier();
        IdempotentTestApplication.OrderRequest request =
                new IdempotentTestApplication.OrderRequest(unique("CONCURRENT-1"), 100L, 1L);
        int threads = 16;
        int beforeExecutions = orderService.slowExecutions().get();
        long beforeDuplicates = metrics.snapshot().duplicates();
        Set<String> beforeKeys = idempotentKeys();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return orderService.createSlow(request);
                } catch (Throwable throwable) {
                    return throwable;
                }
            }));
        }
        start.countDown();
        int rejected = 0;
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline && rejected < threads - 1) {
            rejected = countDuplicates(futures);
            Thread.sleep(20L);
        }
        orderService.releaseBarrier();
        List<Object> results = collect(futures);
        pool.shutdown();

        assertThat(orderService.slowExecutions().get() - beforeExecutions).as("真正执行的次数").isEqualTo(1);
        assertThat(results).filteredOn(result -> result instanceof Result<?>).hasSize(1);
        assertThat(results).filteredOn(result -> result instanceof DuplicateSubmitException).hasSize(threads - 1);
        DuplicateSubmitException duplicate = (DuplicateSubmitException) results.stream()
                .filter(result -> result instanceof DuplicateSubmitException).findFirst().orElseThrow();
        assertThat(duplicate.getMessage()).as("重复提示").isEqualTo("请勿重复提交，请稍后再试");
        assertThat(duplicate.code()).isEqualTo(ResultCode.CONFLICT.code());
        assertThat(metrics.snapshot().duplicates() - beforeDuplicates).as("重复请求指标").isEqualTo(threads - 1);
        Set<String> created = new java.util.HashSet<>(idempotentKeys());
        created.removeAll(beforeKeys);
        assertThat(created).as("幂等键必须写入真实 Redis 并统一前缀").isNotEmpty();
        assertThat(created).allMatch(key -> key.startsWith(CacheKeys.IDEMPOTENT));
    }

    @Test
    void replayedRequestReturnsFirstResultAfterIntercept() throws Exception {
        IdempotentTestApplication.OrderRequest request =
                new IdempotentTestApplication.OrderRequest(unique("CONCURRENT-2"), 100L, 1L);
        int before = orderService.slowExecutions().get();
        orderService.resetBarrier();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return orderService.createSlow(request);
                } catch (Throwable throwable) {
                    return throwable;
                }
            }));
        }
        start.countDown();
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline && countDuplicates(futures) < 3) {
            Thread.sleep(20L);
        }
        orderService.releaseBarrier();
        List<Object> results = collect(futures);
        pool.shutdown();
        assertThat(orderService.slowExecutions().get() - before).isEqualTo(1);

        Result<?> first = (Result<?>) results.stream().filter(result -> result instanceof Result<?>)
                .findFirst().orElseThrow();
        Result<String> repeated = orderService.createSlow(request);
        assertThat(repeated.code()).isEqualTo(ResultCode.SUCCESS.code());
        assertThat(repeated.message()).isEqualTo(ResultCode.SUCCESS.message());
        assertThat(repeated.data()).isEqualTo("慢订单-" + request.orderNo());
        assertThat(repeated.timestamp()).as("必须保留首次结果的原始 Result 结构")
                .isEqualTo(first.timestamp());
    }

    @Test
    void paramOrderIgnoredWhenContentEquals() {
        String mapOrderNo = unique("MAP-ORDER-1");
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("orderNo", mapOrderNo);
        first.put("amount", 10L);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("amount", 10L);
        second.put("orderNo", mapOrderNo);
        int before = orderService.mapExecutions().get();
        Result<String> firstResult = orderService.createByMap(first);
        Result<String> secondResult = orderService.createByMap(second);
        assertThat(orderService.mapExecutions().get() - before).as("字段顺序不同必须判为同一请求").isEqualTo(1);
        assertThat(secondResult).isEqualTo(firstResult);
    }

    @Test
    void ignoredFieldsMakeTimestampOnlyDifferenceSame() {
        String ignoreOrderNo = unique("IGNORE-1");
        int before = orderService.ignoreExecutions().get();
        Result<String> first = orderService.createIgnoreTimestamp(
                new IdempotentTestApplication.OrderRequest(ignoreOrderNo, 20L, 111L));
        Result<String> second = orderService.createIgnoreTimestamp(
                new IdempotentTestApplication.OrderRequest(ignoreOrderNo, 20L, 999L));
        assertThat(orderService.ignoreExecutions().get() - before).isEqualTo(1);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void differentParamsNotBlockedIndiscriminately() {
        String diffFirst = unique("DIFF-1");
        String diffSecond = unique("DIFF-2");
        int before = orderService.normalExecutions().get();
        Result<String> first = orderService.create(new IdempotentTestApplication.OrderRequest(diffFirst, 10L, 1L));
        Result<String> second = orderService.create(new IdempotentTestApplication.OrderRequest(diffSecond, 10L, 1L));
        assertThat(orderService.normalExecutions().get() - before).as("不同参数必须都真正执行").isEqualTo(2);
        assertThat(first.data()).isEqualTo("订单-" + diffFirst);
        assertThat(second.data()).isEqualTo("订单-" + diffSecond);
    }

    @Test
    void customDuplicateHintFromAnnotationTakesEffect() throws Exception {
        orderService.resetBarrier();
        IdempotentTestApplication.OrderRequest request =
                new IdempotentTestApplication.OrderRequest(unique("MESSAGE-1"), 10L, 1L);
        int before = orderService.messageExecutions().get();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return orderService.createWithMessage(request);
                } catch (Throwable throwable) {
                    return throwable;
                }
            }));
        }
        start.countDown();
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline && countDuplicates(futures) < 1) {
            Thread.sleep(20L);
        }
        orderService.releaseBarrier();
        List<Object> results = collect(futures);
        pool.shutdown();
        assertThat(orderService.messageExecutions().get() - before).isEqualTo(1);
        DuplicateSubmitException duplicate = (DuplicateSubmitException) results.stream()
                .filter(result -> result instanceof DuplicateSubmitException).findFirst().orElseThrow();
        assertThat(duplicate.getMessage()).isEqualTo("订单重复提交，请勿重复点击");
    }

    @Test
    void tokenModeFirstConsumeSucceedsSecondRejected() {
        String orderNo = unique("TOKEN-ORDER-1");
        String otherOrderNo = unique("TOKEN-ORDER-2");
        IdempotentToken token = tokenService.issue(null);
        assertThat(token.token()).isNotBlank();
        assertThat(token.ttlSeconds()).isPositive();
        int before = tokenOrderService.tokenExecutions().get();

        Result<String> first = tokenOrderService.submitByToken(token.token(), orderNo);
        assertThat(first.data()).isEqualTo("Token订单-" + orderNo);
        assertThat(tokenOrderService.tokenExecutions().get() - before).isEqualTo(1);

        assertThat(tokenService.exists(token.token())).as("Token 已被消费").isFalse();
        assertThat(tokenService.consume(token.token())).as("二次消费必须被拒").isFalse();

        Result<String> second = tokenOrderService.submitByToken(token.token(), orderNo);
        assertThat(tokenOrderService.tokenExecutions().get() - before).as("二次提交不得再次执行").isEqualTo(1);
        assertThat(second).as("二次提交返回首次结果").isEqualTo(first);

        Result<String> third = tokenOrderService.submitByToken(token.token(), otherOrderNo);
        assertThat(tokenOrderService.tokenExecutions().get() - before)
                .as("已消费的 Token 换参数也不会再次执行").isEqualTo(1);
        assertThat(third).as("已消费的 Token 再次提交仍返回首次结果").isEqualTo(first);
    }

    @Test
    void missingTokenGivesChineseHint() {
        assertThatThrownBy(() -> tokenOrderService.submitByToken(null, unique("TOKEN-MISSING-1")))
                .isInstanceOf(cn.slainc.core.exception.BizException.class)
                .hasMessageContaining("缺少幂等 Token");
    }

    @Test
    void hybridModeChecksTokenAndFingerprint() {
        String orderNo = unique("HYBRID-1");
        String otherOrderNo = unique("HYBRID-2");
        IdempotentToken token = tokenService.issue(null);
        int before = tokenOrderService.hybridExecutions().get();
        Result<String> first = tokenOrderService.submitHybrid(token.token(), orderNo);
        assertThat(first.data()).isEqualTo("混合订单-" + orderNo);
        assertThat(tokenOrderService.hybridExecutions().get() - before).isEqualTo(1);

        Result<String> repeated = tokenOrderService.submitHybrid(token.token(), orderNo);
        assertThat(repeated).as("同 Token 同参数返回首次结果").isEqualTo(first);
        assertThatThrownBy(() -> tokenOrderService.submitHybrid(token.token(), otherOrderNo))
                .as("同一 Token 换参数必须被拒").isInstanceOf(DuplicateSubmitException.class)
                .hasMessage("请勿重复提交，请稍后再试");
        assertThat(tokenOrderService.hybridExecutions().get() - before).isEqualTo(1);
    }

    @Test
    void firstFailureNotConsumedAndRetryAllowed() {
        retryOrderService.failTimes(1);
        IdempotentTestApplication.OrderRequest request =
                new IdempotentTestApplication.OrderRequest(unique("RETRY-OK-1"), 1L, 1L);
        int before = retryOrderService.executions().get();
        assertThatThrownBy(() -> retryOrderService.execute(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("模拟首次执行失败");
        Result<String> retried = retryOrderService.execute(request);
        assertThat(retried.data()).isEqualTo("重试成功-" + request.orderNo());
        assertThat(retryOrderService.executions().get() - before).as("首次失败 + 一次重试").isEqualTo(2);
    }

    @Test
    void requestRejectedAfterMaxRetries() {
        retryOrderService.failTimes(100);
        IdempotentTestApplication.OrderRequest request =
                new IdempotentTestApplication.OrderRequest(unique("RETRY-EXCEED-1"), 1L, 1L);
        int before = retryOrderService.executions().get();
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> retryOrderService.execute(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("模拟首次执行失败");
        }
        assertThatThrownBy(() -> retryOrderService.execute(request))
                .as("maxRetries=2 时第 4 次必须被拒绝")
                .isInstanceOf(IdempotentRetryExceededException.class)
                .hasMessageContaining("失败重试次数已超过上限");
        assertThat(retryOrderService.executions().get() - before).isEqualTo(3);
    }

    @Test
    void executionAllowedAfterRetryWindowExpiry() throws Exception {
        retryOrderService.failTimes(100);
        IdempotentTestApplication.OrderRequest request =
                new IdempotentTestApplication.OrderRequest(unique("RETRY-WINDOW-1"), 1L, 1L);
        assertThatThrownBy(() -> retryOrderService.executeNoRetry(request))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> retryOrderService.executeNoRetry(request))
                .as("maxRetries=0 时第二次必须被拒绝")
                .isInstanceOf(IdempotentRetryExceededException.class);
        Thread.sleep(1300L);
        retryOrderService.failTimes(0);
        assertThat(retryOrderService.executeNoRetry(request).data()).isEqualTo("无重试订单-" + request.orderNo());
    }

    @Test
    void dynamicConfigOverridesWindowAndSwitch() {
        String methodId = IdempotentTestApplication.OrderService.class.getName() + "#createWithDefaultTtl";
        String ttlKey = "modality.idempotent.methods." + methodId + ".default-ttl";
        dynamicConfigService.set(ttlKey, "2s");
        try {
            Set<String> before = idempotentKeys();
            orderService.createWithDefaultTtl(new IdempotentTestApplication.OrderRequest(unique("DYNAMIC-1"), 1L, 1L));
            Set<String> created = idempotentKeys();
            created.removeAll(before);
            assertThat(created).as("应写入新的幂等键").hasSize(1);
            long ttl = redisTemplate.getExpire(created.iterator().next(), TimeUnit.SECONDS);
            assertThat(ttl).as("动态配置的 2s 窗口必须生效").isBetween(1L, 3L);
        } finally {
            dynamicConfigService.remove(ttlKey);
        }

        dynamicConfigService.set("modality.idempotent.enabled", "false");
        try {
            IdempotentTestApplication.OrderRequest request =
                    new IdempotentTestApplication.OrderRequest(unique("DYNAMIC-OFF-1"), 1L, 1L);
            int before = orderService.normalExecutions().get();
            orderService.create(request);
            orderService.create(request);
            assertThat(orderService.normalExecutions().get() - before)
                    .as("总开关关闭后不再拦截").isEqualTo(2);
        } finally {
            dynamicConfigService.remove("modality.idempotent.enabled");
        }
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Set<String> idempotentKeys() {
        Set<String> keys = redisTemplate.keys(CacheKeys.IDEMPOTENT + "*");
        return keys == null ? Set.of() : keys;
    }

    private static int countDuplicates(List<Future<Object>> futures) {
        int count = 0;
        for (Future<Object> future : futures) {
            if (!future.isDone()) {
                continue;
            }
            try {
                if (future.get() instanceof DuplicateSubmitException) {
                    count++;
                }
            } catch (Exception ignored) {
                // 未完成的线程继续等待
            }
        }
        return count;
    }

    private static List<Object> collect(List<Future<Object>> futures) throws Exception {
        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        return results;
    }
}