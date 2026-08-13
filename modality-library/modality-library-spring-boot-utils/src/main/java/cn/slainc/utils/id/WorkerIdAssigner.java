package cn.slainc.utils.id;

public interface WorkerIdAssigner {

    long assignWorkerId();

    default long assignDatacenterId() {
        return 0L;
    }
}
