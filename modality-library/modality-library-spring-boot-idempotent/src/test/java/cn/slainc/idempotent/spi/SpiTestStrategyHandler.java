package cn.slainc.idempotent.spi;

import cn.slainc.idempotent.support.IdempotentContext;

public class SpiTestStrategyHandler implements IdempotentStrategyHandler {

    @Override
    public String name() {
        return "test-spi";
    }

    @Override
    public IdempotentKey resolve(IdempotentContext context) {
        return new IdempotentKey("spi:" + context.methodId(), null, null);
    }
}
