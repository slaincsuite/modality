package cn.slainc.idempotent.support;

import cn.slainc.core.result.Result;
import cn.slainc.idempotent.annotation.Idempotent;
import cn.slainc.idempotent.annotation.IdempotentStrategy;
import cn.slainc.idempotent.autoconfigure.IdempotentProperties;
import cn.slainc.idempotent.exception.DuplicateSubmitException;
import cn.slainc.idempotent.key.ParameterFingerprint;
import cn.slainc.idempotent.metrics.IdempotentMetrics;
import cn.slainc.idempotent.metrics.LocalIdempotentMetrics;
import cn.slainc.idempotent.spi.IdempotentKey;
import cn.slainc.idempotent.spi.IdempotentStrategyHandler;
import cn.slainc.idempotent.spi.IdempotentStrategyRegistry;
import cn.slainc.idempotent.spi.ParameterStrategyHandler;
import cn.slainc.idempotent.spi.TokenStrategyHandler;
import cn.slainc.idempotent.store.IdempotentStore;
import cn.slainc.idempotent.store.LocalIdempotentStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentExecutorTest {

    private static final String KEY_PREFIX = "modality:idempotent:";

    @Test
    void 重复请求返回首次结果并保留Result结构() throws Throwable {
        LocalIdempotentStore store = new LocalIdempotentStore();
        IdempotentExecutor executor = executor(store, new IdempotentProperties());
        AtomicInteger executions = new AtomicInteger();
        IdempotentContext context = context(annotation("plain"), "载荷-1");
        Object first = executor.execute(context, () -> {
            executions.incrementAndGet();
            return Result.ok("首次结果");
        });
        Object second = executor.execute(context, () -> {
            executions.incrementAndGet();
            return Result.ok("第二次结果");
        });
        assertThat(executions.get()).isEqualTo(1);
        assertThat(second).isEqualTo(first);
        assertThat(((Result<?>) second).data()).isEqualTo("首次结果");
        assertThat(((Result<?>) second).code()).isEqualTo(200);
    }

    @Test
    void 结果缓存关闭时重复请求直接报重复() throws Throwable {
        LocalIdempotentStore store = new LocalIdempotentStore();
        IdempotentProperties properties = new IdempotentProperties();
        properties.setCacheResult(false);
        IdempotentExecutor executor = executor(store, properties);
        AtomicInteger executions = new AtomicInteger();
        IdempotentContext context = context(annotation("plain"), "载荷-2");
        executor.execute(context, () -> {
            executions.incrementAndGet();
            return Result.ok("首次");
        });
        assertThatThrownBy(() -> executor.execute(context, () -> {
            executions.incrementAndGet();
            return Result.ok("第二次");
        })).isInstanceOf(DuplicateSubmitException.class).hasMessage("请勿重复提交，请稍后再试");
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void 存储不可用时默认放行() throws Throwable {
        IdempotentExecutor executor = executor(new BrokenIdempotentStore(), new IdempotentProperties());
        AtomicInteger executions = new AtomicInteger();
        Object result = executor.execute(context(annotation("plain"), "载荷-3"), () -> {
            executions.incrementAndGet();
            return Result.ok("降级执行");
        });
        assertThat(executions.get()).isEqualTo(1);
        assertThat(((Result<?>) result).data()).isEqualTo("降级执行");
    }

    @Test
    void 关闭failOpen后存储异常直接抛出() {
        IdempotentProperties properties = new IdempotentProperties();
        properties.setFailOpen(false);
        IdempotentExecutor executor = executor(new BrokenIdempotentStore(), properties);
        assertThatThrownBy(() -> executor.execute(context(annotation("plain"), "载荷-4"), () -> Result.ok("不会执行")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("模拟存储不可用");
    }

    @Test
    void 自定义SPI处理器真正参与去重() throws Throwable {
        LocalIdempotentStore store = new LocalIdempotentStore();
        IdempotentExecutor executor = executor(store, new IdempotentProperties(), new FixedKeyHandler());
        AtomicInteger executions = new AtomicInteger();
        IdempotentContext context = context(annotation("custom"), "载荷-5");
        Object first = executor.execute(context, () -> {
            executions.incrementAndGet();
            return Result.ok("自定义策略首次");
        });
        Object second = executor.execute(context, () -> {
            executions.incrementAndGet();
            return Result.ok("自定义策略第二次");
        });
        assertThat(executions.get()).as("自定义处理器提供的幂等键必须真正生效").isEqualTo(1);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void 重复请求指标被统计() throws Throwable {
        LocalIdempotentStore store = new LocalIdempotentStore();
        LocalIdempotentMetrics metrics = new LocalIdempotentMetrics();
        IdempotentExecutor executor = executor(store, new IdempotentProperties(), metrics);
        IdempotentContext context = context(annotation("plain"), "载荷-6");
        executor.execute(context, () -> Result.ok("首次"));
        executor.execute(context, () -> Result.ok("第二次"));
        assertThat(metrics.snapshot().requests()).isEqualTo(2L);
        assertThat(metrics.snapshot().duplicates()).isEqualTo(1L);
        assertThat(metrics.snapshot().cacheHits()).isEqualTo(1L);
        assertThat(metrics.snapshot().executed()).isEqualTo(1L);
        assertThat(metrics.snapshot().duplicateRate()).isEqualTo(0.5D);
    }

    private IdempotentExecutor executor(IdempotentStore store, IdempotentProperties properties,
                                        IdempotentStrategyHandler... extraHandlers) {
        IdempotentStrategyRegistry registry = new IdempotentStrategyRegistry(List.of(
                new ParameterStrategyHandler(new ParameterFingerprint(), KEY_PREFIX),
                new TokenStrategyHandler(KEY_PREFIX, true)));
        for (IdempotentStrategyHandler handler : extraHandlers) {
            registry.register(handler);
        }
        return new IdempotentExecutor(store, registry,
                new IdempotentSettingsResolver(properties, TestObjectProviders.empty()),
                new IdempotentResultCodec(), TestObjectProviders.empty());
    }

    private IdempotentExecutor executor(IdempotentStore store, IdempotentProperties properties,
                                        LocalIdempotentMetrics metrics) {
        IdempotentStrategyRegistry registry = new IdempotentStrategyRegistry(List.of(
                new ParameterStrategyHandler(new ParameterFingerprint(), KEY_PREFIX)));
        return new IdempotentExecutor(store, registry,
                new IdempotentSettingsResolver(properties, TestObjectProviders.empty()),
                new IdempotentResultCodec(),
                TestObjectProviders.<IdempotentMetrics>of(metrics));
    }

    private IdempotentContext context(Idempotent annotation, String payload) throws Exception {
        Method method = getClass().getDeclaredMethod("handle", Object.class);
        return new IdempotentContext(method, new Object[]{payload}, this, annotation,
                method.getDeclaringClass().getName() + "#" + method.getName(), "/demo/handle", 1001L, null, null, null,
                null);
    }

    private Idempotent annotation(String name) throws Exception {
        return getClass().getDeclaredMethod(name).getAnnotation(Idempotent.class);
    }

    @SuppressWarnings("unused")
    private void handle(Object payload) {
    }

    @Idempotent(ttl = 60)
    @SuppressWarnings("unused")
    private void plain() {
    }

    @Idempotent(ttl = 60, strategy = IdempotentStrategy.CUSTOM, handler = "fixed-key")
    @SuppressWarnings("unused")
    private void custom() {
    }

    static class FixedKeyHandler implements IdempotentStrategyHandler {

        @Override
        public String name() {
            return "fixed-key";
        }

        @Override
        public IdempotentKey resolve(IdempotentContext context) {
            return new IdempotentKey(KEY_PREFIX + "custom:" + context.methodId(), null, null);
        }

        @Override
        public boolean acquire(IdempotentContext context, IdempotentKey key) {
            return context.store().tryAcquire(key.stateKey(), Duration.ofSeconds(60));
        }
    }
}
