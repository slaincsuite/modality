package cn.slainc.idempotent.spi;

import cn.slainc.idempotent.annotation.IdempotentStrategy;
import cn.slainc.idempotent.support.IdempotentSettings;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public class IdempotentStrategyRegistry {

    private static final Logger log = LoggerFactory.getLogger(IdempotentStrategyRegistry.class);

    private final Map<String, IdempotentStrategyHandler> handlers = new ConcurrentHashMap<>();

    private final Map<IdempotentStrategy, IdempotentStrategyHandler> byStrategy = new ConcurrentHashMap<>();

    public IdempotentStrategyRegistry(List<IdempotentStrategyHandler> beans) {
        this(beans, true);
    }

    public IdempotentStrategyRegistry(List<IdempotentStrategyHandler> beans, boolean serviceLoader) {
        if (serviceLoader) {
            loadServiceProviders();
        }
        if (beans != null) {
            beans.forEach(this::register);
        }
    }

    public void register(IdempotentStrategyHandler handler) {
        if (handler == null) {
            return;
        }
        if (StringUtils.isBlank(handler.name())) {
            throw new IllegalArgumentException("幂等策略处理器必须提供名称");
        }
        IdempotentStrategyHandler previous = handlers.put(handler.name(), handler);
        if (previous != null && previous != handler) {
            log.info("幂等策略处理器 {} 已被 {} 覆盖", handler.name(), handler.getClass().getName());
        }
        IdempotentStrategy strategy = handler.strategy();
        if (strategy != null && strategy != IdempotentStrategy.CUSTOM) {
            byStrategy.put(strategy, handler);
        }
    }

    public Optional<IdempotentStrategyHandler> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(handlers.get(name));
    }

    public IdempotentStrategyHandler resolve(IdempotentSettings settings) {
        if (settings.strategy() == IdempotentStrategy.CUSTOM) {
            if (StringUtils.isBlank(settings.handler())) {
                throw new IllegalStateException("使用 CUSTOM 策略时必须通过 @Idempotent(handler = \"名称\") 指定处理器");
            }
            return find(settings.handler()).orElseThrow(() -> new IllegalStateException(
                    "未找到自定义幂等策略处理器：" + settings.handler() + "，已注册：" + handlers.keySet()));
        }
        IdempotentStrategyHandler handler = byStrategy.get(settings.strategy());
        if (handler == null) {
            throw new IllegalStateException("未找到幂等策略处理器：" + settings.strategy().description());
        }
        return handler;
    }

    public Collection<IdempotentStrategyHandler> handlers() {
        return List.copyOf(handlers.values());
    }

    private void loadServiceProviders() {
        try {
            for (IdempotentStrategyHandler handler : ServiceLoader.load(IdempotentStrategyHandler.class)) {
                register(handler);
            }
        } catch (Throwable throwable) {
            log.warn("通过 ServiceLoader 加载幂等策略处理器失败，原因={}", throwable.getMessage());
        }
    }
}
