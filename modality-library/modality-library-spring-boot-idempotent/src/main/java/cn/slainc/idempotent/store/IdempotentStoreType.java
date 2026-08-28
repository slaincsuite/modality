package cn.slainc.idempotent.store;

public enum IdempotentStoreType {

    REDIS("Redis（多节点共享）"),
    DATABASE("数据库（JdbcTemplate）"),
    LOCAL("本地缓存（单机）");

    private final String description;

    IdempotentStoreType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
