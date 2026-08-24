package cn.slainc.redis.lock;

import cn.slainc.redis.autoconfigure.RedisProperties;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class DistributedLockTemplate {

    private final RedissonClient redissonClient;

    private final RedisProperties properties;

    private final LockStats stats = new LockStats();

    public DistributedLockTemplate(RedissonClient redissonClient, RedisProperties properties) {
        if (redissonClient == null) {
            throw new IllegalArgumentException("RedissonClient 不能为空");
        }
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    public LockStats getStats() {
        return stats;
    }

    public LockHandle acquire(String lockKey, LockType type) {
        return acquire(lockKey,
                properties.getLock().getWaitTime(),
                properties.getLock().getLeaseTime(),
                type);
    }

    public LockHandle acquire(String lockKey, long waitSeconds, long leaseSeconds, LockType type) {
        if (lockKey == null || lockKey.isBlank()) {
            throw new IllegalArgumentException("分布式锁的键不能为空");
        }
        RLock lock = resolveLock(lockKey, type);
        long start = System.currentTimeMillis();
        boolean locked;
        try {
            if (leaseSeconds <= 0L) {
                // leaseTime 为负时 Redisson 启用看门狗自动续期
                locked = lock.tryLock(Math.max(waitSeconds, 0L), TimeUnit.SECONDS);
            } else {
                locked = lock.tryLock(Math.max(waitSeconds, 0L), leaseSeconds, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            stats.recordFailure(System.currentTimeMillis() - start);
            throw new LockAcquireException("获取分布式锁被中断：" + lockKey, exception);
        } catch (Exception exception) {
            stats.recordFailure(System.currentTimeMillis() - start);
            throw new LockAcquireException("获取分布式锁异常：" + lockKey + "，原因=" + exception.getMessage(), exception);
        }
        long waitMillis = System.currentTimeMillis() - start;
        if (locked) {
            stats.recordSuccess(waitMillis);
            if (leaseSeconds <= 0L) {
                stats.recordWatchdogRenewal();
            }
        } else {
            stats.recordFailure(waitMillis);
        }
        return new LockHandle(lock, locked);
    }

    public <T> T executeWithLock(String lockKey, Supplier<T> action) {
        return executeWithLock(lockKey,
                properties.getLock().getWaitTime(),
                properties.getLock().getLeaseTime(),
                LockType.REENTRANT,
                action);
    }

    public <T> T executeWithLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> action) {
        return executeWithLock(lockKey, waitSeconds, leaseSeconds, LockType.REENTRANT, action);
    }

    public <T> T executeWithLock(String lockKey, long waitSeconds, long leaseSeconds, LockType type,
                                 Supplier<T> action) {
        try (LockHandle handle = acquire(lockKey, waitSeconds, leaseSeconds, type)) {
            if (!handle.isLocked()) {
                throw new LockAcquireException("获取分布式锁失败：" + lockKey);
            }
            return action.get();
        }
    }

    public <T> T executeWithWriteLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> action) {
        return executeWithLock(lockKey, waitSeconds, leaseSeconds, LockType.WRITE, action);
    }

    public <T> T executeWithReadLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> action) {
        return executeWithLock(lockKey, waitSeconds, leaseSeconds, LockType.READ, action);
    }

    public boolean isLocked(String lockKey) {
        return redissonClient.getLock(lockKey).isLocked();
    }

    public boolean forceUnlock(String lockKey) {
        return redissonClient.getLock(lockKey).forceUnlock();
    }

    public RLock getLock(String lockKey) {
        return redissonClient.getLock(lockKey);
    }

    public RReadWriteLock getReadWriteLock(String lockKey) {
        return redissonClient.getReadWriteLock(lockKey);
    }

    public RSemaphore getSemaphore(String name) {
        return redissonClient.getSemaphore(name);
    }

    public RCountDownLatch getCountDownLatch(String name) {
        return redissonClient.getCountDownLatch(name);
    }

    public RAtomicLong getAtomicLong(String name) {
        return redissonClient.getAtomicLong(name);
    }

    public RedissonClient getRedissonClient() {
        return redissonClient;
    }

    private RLock resolveLock(String lockKey, LockType type) {
        if (type == LockType.READ) {
            return redissonClient.getReadWriteLock(lockKey).readLock();
        }
        if (type == LockType.WRITE) {
            return redissonClient.getReadWriteLock(lockKey).writeLock();
        }
        return redissonClient.getLock(lockKey);
    }
}
