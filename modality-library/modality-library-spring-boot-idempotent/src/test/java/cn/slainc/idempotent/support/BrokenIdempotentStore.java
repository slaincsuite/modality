package cn.slainc.idempotent.support;

import cn.slainc.idempotent.store.IdempotentRecord;
import cn.slainc.idempotent.store.IdempotentStore;

import java.time.Duration;
import java.util.Optional;

public class BrokenIdempotentStore implements IdempotentStore {

    private final boolean failOnAcquire;

    public BrokenIdempotentStore() {
        this(true);
    }

    public BrokenIdempotentStore(boolean failOnAcquire) {
        this.failOnAcquire = failOnAcquire;
    }

    @Override
    public String name() {
        return "broken";
    }

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        if (failOnAcquire) {
            throw new IllegalStateException("模拟存储不可用");
        }
        return true;
    }

    @Override
    public void release(String key) {
        throw new IllegalStateException("模拟存储不可用");
    }

    @Override
    public boolean isHeld(String key) {
        return false;
    }

    @Override
    public Optional<IdempotentRecord> findRecord(String key) {
        throw new IllegalStateException("模拟存储不可用");
    }

    @Override
    public void saveRecord(String key, IdempotentRecord record, Duration ttl) {
        throw new IllegalStateException("模拟存储不可用");
    }

    @Override
    public long increaseFailure(String key, Duration window) {
        throw new IllegalStateException("模拟存储不可用");
    }

    @Override
    public long failureCount(String key) {
        throw new IllegalStateException("模拟存储不可用");
    }

    @Override
    public void clearFailure(String key) {
        throw new IllegalStateException("模拟存储不可用");
    }

    @Override
    public void saveToken(String token, String owner, Duration ttl) {
        throw new IllegalStateException("模拟存储不可用");
    }

    @Override
    public String consumeToken(String token) {
        throw new IllegalStateException("模拟存储不可用");
    }

    @Override
    public void clear(String key) {
        throw new IllegalStateException("模拟存储不可用");
    }
}
