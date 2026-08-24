package cn.slainc.redis.lock;

import org.redisson.api.RLock;

public class LockHandle implements AutoCloseable {

    private final RLock lock;

    private final boolean locked;

    LockHandle(RLock lock, boolean locked) {
        this.lock = lock;
        this.locked = locked;
    }

    public boolean isLocked() {
        return locked;
    }

    public String getName() {
        return lock.getName();
    }

    @Override
    public void close() {
        if (locked && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
