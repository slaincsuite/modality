package cn.slainc.idempotent.spi;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.idempotent.support.IdempotentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTokenStrategyHandler implements IdempotentStrategyHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractTokenStrategyHandler.class);

    private final boolean bindUser;

    protected AbstractTokenStrategyHandler(boolean bindUser) {
        this.bindUser = bindUser;
    }

    protected String requireToken(IdempotentContext context) {
        if (!context.hasToken()) {
            throw new BizException(ResultCode.PARAM_MISSING,
                    "缺少幂等 Token，请先由 IdempotentTokenService 申请，并以参数或请求头方式提交");
        }
        return context.token();
    }

    protected boolean consumeToken(IdempotentContext context, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String owner = context.store().consumeToken(token);
        if (owner == null) {
            return false;
        }
        if (bindUser && !owner.isBlank() && !owner.equals(owner(context))) {
            log.warn("幂等 Token 归属校验失败已拒绝，使用者={}，申请者={}", owner(context), owner);
            return false;
        }
        return true;
    }

    protected void reissueToken(IdempotentContext context, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            context.store().saveToken(token, owner(context), context.settings().ttl());
        } catch (Exception exception) {
            log.warn("幂等 Token 回补失败，失败重试将不可用，原因={}", exception.getMessage());
        }
    }

    protected String owner(IdempotentContext context) {
        return context.userId() == null ? "" : String.valueOf(context.userId());
    }
}
