package cn.slainc.idempotent.support;

import cn.slainc.idempotent.annotation.IdempotentStrategy;

import java.time.Duration;
import java.util.List;

public record IdempotentSettings(
        boolean enabled,
        IdempotentStrategy strategy,
        String handler,
        String message,
        Duration ttl,
        int maxRetries,
        Duration retryWindow,
        List<String> ignoreFields,
        boolean includeUserId,
        boolean includePath,
        boolean cacheResult,
        boolean failOpen,
        boolean logDuplicate) {

    public IdempotentSettings {
        ignoreFields = ignoreFields == null ? List.of() : List.copyOf(ignoreFields);
    }

    public long ttlMillis() {
        return ttl == null ? 0L : ttl.toMillis();
    }

    public long retryWindowMillis() {
        return retryWindow == null ? 0L : retryWindow.toMillis();
    }
}
