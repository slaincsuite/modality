package cn.slainc.idempotent.spi;

import cn.slainc.idempotent.annotation.IdempotentStrategy;
import cn.slainc.idempotent.key.ParameterFingerprint;
import cn.slainc.idempotent.support.IdempotentContext;
import cn.slainc.idempotent.support.IdempotentKeys;

public class ParameterStrategyHandler implements IdempotentStrategyHandler {

    private final ParameterFingerprint fingerprint;

    private final String keyPrefix;

    public ParameterStrategyHandler(ParameterFingerprint fingerprint, String keyPrefix) {
        if (fingerprint == null) {
            throw new IllegalArgumentException("ParameterFingerprint 不能为空");
        }
        this.fingerprint = fingerprint;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public String name() {
        return "parameter";
    }

    @Override
    public IdempotentStrategy strategy() {
        return IdempotentStrategy.PARAMETER;
    }

    @Override
    public IdempotentKey resolve(IdempotentContext context) {
        String value = fingerprint.fingerprint(context);
        return new IdempotentKey(IdempotentKeys.stateKey(keyPrefix, "p:" + value), value, null);
    }
}
