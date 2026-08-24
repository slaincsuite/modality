package cn.slainc.redis.lock;

public enum LockType {

    REENTRANT("可重入互斥锁"),
    READ("读写锁读锁"),
    WRITE("读写锁写锁");

    private final String description;

    LockType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
