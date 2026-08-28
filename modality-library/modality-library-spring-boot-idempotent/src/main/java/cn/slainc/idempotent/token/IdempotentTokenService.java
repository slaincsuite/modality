package cn.slainc.idempotent.token;

import cn.slainc.core.context.UserContext;
import cn.slainc.idempotent.autoconfigure.IdempotentProperties;
import cn.slainc.idempotent.store.IdempotentStore;
import cn.slainc.idempotent.support.IdempotentKeys;
import cn.slainc.utils.string.StringUtils;

import java.time.Duration;
import java.util.UUID;

public class IdempotentTokenService {

    private static final long MAX_TTL_SECONDS = 7L * 24L * 60L * 60L;

    private final IdempotentStore store;

    private final IdempotentProperties properties;

    public IdempotentTokenService(IdempotentStore store, IdempotentProperties properties) {
        if (store == null) {
            throw new IllegalArgumentException("IdempotentStore 不能为空");
        }
        this.store = store;
        this.properties = properties;
    }

    public IdempotentToken issue(Long ttlSeconds) {
        long ttl = ttlSeconds == null || ttlSeconds <= 0L
                ? properties.getToken().getDefaultTtl().getSeconds() : Math.min(ttlSeconds, MAX_TTL_SECONDS);
        String token = UUID.randomUUID().toString().replace("-", "");
        store.saveToken(token, owner(), Duration.ofSeconds(ttl));
        return new IdempotentToken(token, properties.getToken().getHeaderName(), ttl,
                System.currentTimeMillis() + ttl * 1000L);
    }

    public boolean consume(String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }
        return store.consumeToken(token) != null;
    }

    public boolean exists(String token) {
        return StringUtils.isNotBlank(token) && store.isHeld(IdempotentKeys.tokenKey(token));
    }

    private String owner() {
        return UserContext.userId() == null ? "" : String.valueOf(UserContext.userId());
    }
}
