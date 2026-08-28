package cn.slainc.idempotent.support;

import cn.slainc.idempotent.exception.DuplicateSubmitException;
import cn.slainc.idempotent.exception.IdempotentRetryExceededException;
import cn.slainc.idempotent.metrics.IdempotentMetrics;
import cn.slainc.idempotent.metrics.LocalIdempotentMetrics;
import cn.slainc.idempotent.spi.IdempotentKey;
import cn.slainc.idempotent.spi.IdempotentStrategyHandler;
import cn.slainc.idempotent.spi.IdempotentStrategyRegistry;
import cn.slainc.idempotent.store.IdempotentRecord;
import cn.slainc.idempotent.store.IdempotentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

public class IdempotentExecutor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentExecutor.class);

    private static final IdempotentMetrics FALLBACK_METRICS = new LocalIdempotentMetrics();

    private final IdempotentStore store;

    private final IdempotentStrategyRegistry registry;

    private final IdempotentSettingsResolver settingsResolver;

    private final IdempotentResultCodec resultCodec;

    private final ObjectProvider<IdempotentMetrics> metricsProvider;

    public IdempotentExecutor(IdempotentStore store, IdempotentStrategyRegistry registry,
                              IdempotentSettingsResolver settingsResolver, IdempotentResultCodec resultCodec,
                              ObjectProvider<IdempotentMetrics> metricsProvider) {
        if (store == null) {
            throw new IllegalArgumentException("IdempotentStore 不能为空");
        }
        if (registry == null) {
            throw new IllegalArgumentException("IdempotentStrategyRegistry 不能为空");
        }
        this.store = store;
        this.registry = registry;
        this.settingsResolver = settingsResolver;
        this.resultCodec = resultCodec;
        this.metricsProvider = metricsProvider;
    }

    public Object execute(IdempotentContext context, IdempotentInvocation invocation) throws Throwable {
        IdempotentSettings settings = settingsResolver.resolve(context);
        if (!settings.enabled()) {
            return invocation.proceed();
        }
        IdempotentMetrics metrics = metrics();
        String methodId = context.methodId();
        IdempotentContext runtime = context.withRuntime(settings, store);
        metrics.recordRequest(methodId);
        IdempotentStrategyHandler handler = registry.resolve(settings);
        IdempotentKey key = handler.resolve(runtime);
        long failures = safeFailureCount(key.stateKey());
        if (failures > settings.maxRetries()) {
            metrics.recordRetryExceeded(methodId);
            log.warn("失败重试已超出上限，请求被拒绝：方法={}，键={}，失败次数={}，上限={}", methodId, key.stateKey(),
                    failures, settings.maxRetries());
            throw new IdempotentRetryExceededException("失败重试次数已超过上限（" + settings.maxRetries()
                    + " 次），请等待重试窗口结束后再试");
        }
        if (!safeAcquire(handler, runtime, key, settings)) {
            Optional<IdempotentRecord> cached = safeFindRecord(key.stateKey());
            metrics.recordDuplicate(methodId);
            if (settings.cacheResult() && cached.isPresent() && cached.get().hasPayload()) {
                metrics.recordCacheHit(methodId);
                logDuplicate(settings, methodId, key.stateKey(), true);
                return resultCodec.decode(cached.get(), context.method());
            }
            logDuplicate(settings, methodId, key.stateKey(), false);
            throw new DuplicateSubmitException(settings.message());
        }
        try {
            Object result = invocation.proceed();
            if (settings.cacheResult()) {
                safeSaveRecord(key.stateKey(), resultCodec.encode(result, context.method()), settings);
            }
            safeClearFailure(key.stateKey());
            metrics.recordExecuted(methodId);
            return result;
        } catch (Throwable error) {
            long failureCount = safeIncreaseFailure(key.stateKey(), settings);
            safeRelease(handler, runtime, key);
            metrics.recordFailure(methodId);
            log.warn("幂等方法执行失败，占位已释放并允许重试：方法={}，键={}，窗口内失败次数={}，原因={}", methodId,
                    key.stateKey(), failureCount, error.getMessage());
            throw error;
        }
    }

    private boolean safeAcquire(IdempotentStrategyHandler handler, IdempotentContext context, IdempotentKey key,
                                IdempotentSettings settings) {
        try {
            return handler.acquire(context, key);
        } catch (Exception exception) {
            if (settings.failOpen()) {
                log.warn("幂等存储不可用，已放行本次请求：键={}，原因={}", key.stateKey(), exception.getMessage());
                return true;
            }
            throw exception;
        }
    }

    private void safeRelease(IdempotentStrategyHandler handler, IdempotentContext context, IdempotentKey key) {
        try {
            handler.release(context, key);
        } catch (Exception exception) {
            log.warn("幂等占位释放失败，可能影响后续重试：键={}，原因={}", key.stateKey(), exception.getMessage());
        }
    }

    private Optional<IdempotentRecord> safeFindRecord(String key) {
        try {
            return store.findRecord(key);
        } catch (Exception exception) {
            log.warn("读取幂等结果失败，将按重复提交处理：键={}，原因={}", key, exception.getMessage());
            return Optional.empty();
        }
    }

    private void safeSaveRecord(String key, IdempotentRecord record, IdempotentSettings settings) {
        try {
            store.saveRecord(key, record, settings.ttl());
        } catch (Exception exception) {
            log.warn("幂等结果缓存失败，重复提交将不再返回首次结果：键={}，原因={}", key, exception.getMessage());
        }
    }

    private long safeFailureCount(String key) {
        try {
            return store.failureCount(key);
        } catch (Exception exception) {
            log.warn("读取幂等失败次数失败，按 0 处理：键={}，原因={}", key, exception.getMessage());
            return 0L;
        }
    }

    private long safeIncreaseFailure(String key, IdempotentSettings settings) {
        try {
            return store.increaseFailure(key, settings.retryWindow());
        } catch (Exception exception) {
            log.warn("记录幂等失败次数失败：键={}，原因={}", key, exception.getMessage());
            return 0L;
        }
    }

    private void safeClearFailure(String key) {
        try {
            store.clearFailure(key);
        } catch (Exception exception) {
            log.warn("清理幂等失败次数失败：键={}，原因={}", key, exception.getMessage());
        }
    }

    private void logDuplicate(IdempotentSettings settings, String methodId, String key, boolean cached) {
        if (settings.logDuplicate()) {
            log.warn("重复提交已拦截：方法={}，键={}，是否返回首次结果={}", methodId, key, cached);
        }
    }

    private IdempotentMetrics metrics() {
        IdempotentMetrics metrics = metricsProvider == null ? null : metricsProvider.getIfAvailable();
        return metrics == null ? FALLBACK_METRICS : metrics;
    }
}
