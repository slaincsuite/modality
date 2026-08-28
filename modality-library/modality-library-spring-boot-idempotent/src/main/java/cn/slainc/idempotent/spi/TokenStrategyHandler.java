package cn.slainc.idempotent.spi;

import cn.slainc.idempotent.annotation.IdempotentStrategy;
import cn.slainc.idempotent.support.IdempotentContext;
import cn.slainc.idempotent.support.IdempotentKeys;
import cn.slainc.utils.digest.DigestUtils;

public class TokenStrategyHandler extends AbstractTokenStrategyHandler {

    private final String keyPrefix;

    public TokenStrategyHandler(String keyPrefix, boolean bindUser) {
        super(bindUser);
        this.keyPrefix = keyPrefix;
    }

    @Override
    public String name() {
        return "token";
    }

    @Override
    public IdempotentStrategy strategy() {
        return IdempotentStrategy.TOKEN;
    }

    @Override
    public IdempotentKey resolve(IdempotentContext context) {
        String token = requireToken(context);
        return new IdempotentKey(IdempotentKeys.stateKey(keyPrefix, "t:" + DigestUtils.md5(token)), null, token);
    }

    @Override
    public boolean acquire(IdempotentContext context, IdempotentKey key) {
        return consumeToken(context, key.token());
    }

    @Override
    public void release(IdempotentContext context, IdempotentKey key) {
        reissueToken(context, key.token());
    }
}
