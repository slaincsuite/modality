package cn.slainc.idempotent.spi;

import cn.slainc.idempotent.annotation.IdempotentStrategy;
import cn.slainc.idempotent.key.ParameterFingerprint;
import cn.slainc.idempotent.support.IdempotentContext;
import cn.slainc.idempotent.support.IdempotentKeys;
import cn.slainc.utils.digest.DigestUtils;

public class HybridStrategyHandler extends AbstractTokenStrategyHandler {

    private final ParameterFingerprint fingerprint;

    private final String keyPrefix;

    public HybridStrategyHandler(ParameterFingerprint fingerprint, String keyPrefix, boolean bindUser) {
        super(bindUser);
        if (fingerprint == null) {
            throw new IllegalArgumentException("ParameterFingerprint 不能为空");
        }
        this.fingerprint = fingerprint;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public String name() {
        return "hybrid";
    }

    @Override
    public IdempotentStrategy strategy() {
        return IdempotentStrategy.HYBRID;
    }

    @Override
    public IdempotentKey resolve(IdempotentContext context) {
        String token = requireToken(context);
        String value = fingerprint.fingerprint(context);
        String hash = DigestUtils.md5(value + "|" + token);
        return new IdempotentKey(IdempotentKeys.stateKey(keyPrefix, "h:" + hash), value, token);
    }

    @Override
    public boolean acquire(IdempotentContext context, IdempotentKey key) {
        if (!context.store().tryAcquire(key.stateKey(), context.settings().ttl())) {
            return false;
        }
        if (!consumeToken(context, key.token())) {
            context.store().release(key.stateKey());
            return false;
        }
        return true;
    }

    @Override
    public void release(IdempotentContext context, IdempotentKey key) {
        context.store().release(key.stateKey());
        reissueToken(context, key.token());
    }
}
