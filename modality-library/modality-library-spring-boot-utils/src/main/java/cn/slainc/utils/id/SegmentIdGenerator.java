package cn.slainc.utils.id;

import java.util.concurrent.atomic.AtomicLong;

public final class SegmentIdGenerator {

    private final String key;
    private final int step;
    private final SegmentAllocator allocator;
    private final AtomicLong current = new AtomicLong(0L);
    private final Object lock = new Object();

    private volatile long max = 0L;

    public SegmentIdGenerator(String key, int step, SegmentAllocator allocator) {
        if (step <= 0) {
            throw new IllegalArgumentException("步长必须为正数");
        }
        if (allocator == null) {
            throw new IllegalArgumentException("号段分配器不能为空");
        }
        this.key = key;
        this.step = step;
        this.allocator = allocator;
    }

    public long nextId() {
        while (true) {
            long value = current.incrementAndGet();
            if (value <= max) {
                return value;
            }
            synchronized (lock) {
                if (current.get() > max) {
                    long newMax = allocator.nextMaxId(key, step);
                    max = newMax;
                    current.set(newMax - step);
                }
            }
        }
    }

    public String nextIdString() {
        return Long.toString(nextId());
    }

    public long currentMax() {
        return max;
    }

    public int step() {
        return step;
    }

    public String key() {
        return key;
    }
}
