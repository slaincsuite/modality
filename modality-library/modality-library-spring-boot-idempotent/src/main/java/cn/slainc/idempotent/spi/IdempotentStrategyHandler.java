package cn.slainc.idempotent.spi;

import cn.slainc.idempotent.annotation.IdempotentStrategy;
import cn.slainc.idempotent.support.IdempotentContext;

public interface IdempotentStrategyHandler {

    String name();

    default IdempotentStrategy strategy() {
        return IdempotentStrategy.CUSTOM;
    }

    IdempotentKey resolve(IdempotentContext context);

    default boolean acquire(IdempotentContext context, IdempotentKey key) {
        return context.store().tryAcquire(key.stateKey(), context.settings().ttl());
    }

    default void release(IdempotentContext context, IdempotentKey key) {
        context.store().release(key.stateKey());
    }
}
