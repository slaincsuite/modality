package cn.slainc.utils.id;

public final class SnowflakeIdGenerator {

    public static final long DEFAULT_EPOCH = 1704067200000L;

    private static final int WORKER_ID_BITS = 5;
    private static final int DATACENTER_ID_BITS = 5;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final int TIMESTAMP_SHIFT = WORKER_ID_BITS + DATACENTER_ID_BITS + SEQUENCE_BITS;
    private static final int DATACENTER_SHIFT = WORKER_ID_BITS + SEQUENCE_BITS;
    private static final int WORKER_SHIFT = SEQUENCE_BITS;
    private static final long MAX_CLOCK_BACKWARD_MILLIS = 5L;

    private final long workerId;
    private final long datacenterId;
    private final long epoch;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId) {
        this(workerId, 0L, DEFAULT_EPOCH);
    }

    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        this(workerId, datacenterId, DEFAULT_EPOCH);
    }

    public SnowflakeIdGenerator(long workerId, long datacenterId, long epoch) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 必须在 0 到 " + MAX_WORKER_ID + " 之间");
        }
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId 必须在 0 到 " + MAX_DATACENTER_ID + " 之间");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.epoch = epoch;
    }

    public static SnowflakeIdGenerator create(WorkerIdAssigner assigner) {
        if (assigner == null) {
            throw new IllegalArgumentException("workerId 分配器不能为空");
        }
        return new SnowflakeIdGenerator(assigner.assignWorkerId(), assigner.assignDatacenterId());
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > MAX_CLOCK_BACKWARD_MILLIS) {
                throw new IllegalStateException("时钟回拨 " + offset + " 毫秒");
            }
            timestamp = waitUntil(lastTimestamp);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - epoch) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    public String nextIdString() {
        return Long.toString(nextId());
    }

    private long waitUntil(long targetTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp < targetTimestamp) {
            Thread.onSpinWait();
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    public long workerId() {
        return workerId;
    }

    public long datacenterId() {
        return datacenterId;
    }

    public long timestampOf(long id) {
        return (id >> TIMESTAMP_SHIFT) + epoch;
    }

    public long workerIdOf(long id) {
        return (id >> WORKER_SHIFT) & MAX_WORKER_ID;
    }

    public long datacenterIdOf(long id) {
        return (id >> DATACENTER_SHIFT) & MAX_DATACENTER_ID;
    }

    public long sequenceOf(long id) {
        return id & SEQUENCE_MASK;
    }
}
