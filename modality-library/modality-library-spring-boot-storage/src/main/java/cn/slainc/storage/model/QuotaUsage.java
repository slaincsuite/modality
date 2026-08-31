package cn.slainc.storage.model;

public record QuotaUsage(long used, long limit, double ratio, boolean warning, boolean exceeded) {

    public static QuotaUsage of(long used, long limit, double ratio) {
        if (limit <= 0) {
            return new QuotaUsage(used, limit, 0d, false, false);
        }
        double value = (double) used / (double) limit;
        return new QuotaUsage(used, limit, value, value >= ratio, used > limit);
    }
}
