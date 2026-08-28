package cn.slainc.idempotent.store;

import cn.slainc.idempotent.support.IdempotentKeys;

import java.time.Duration;
import java.util.Optional;

public interface IdempotentStore {

    String name();

    boolean tryAcquire(String key, Duration ttl);

    void release(String key);

    boolean isHeld(String key);

    Optional<IdempotentRecord> findRecord(String key);

    void saveRecord(String key, IdempotentRecord record, Duration ttl);

    long increaseFailure(String key, Duration window);

    long failureCount(String key);

    void clearFailure(String key);

    void saveToken(String token, String owner, Duration ttl);

    String consumeToken(String token);

    void clear(String key);

    default void clearToken(String token) {
        clear(IdempotentKeys.tokenKey(token));
    }
}
