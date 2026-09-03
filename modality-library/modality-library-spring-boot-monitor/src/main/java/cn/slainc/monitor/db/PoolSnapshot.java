package cn.slainc.monitor.db;

public record PoolSnapshot(String name, double active, double idle, double pending, double total, double max) {

    public static PoolSnapshot empty(String name) {
        return new PoolSnapshot(name, 0d, 0d, 0d, 0d, 0d);
    }
}
