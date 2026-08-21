package cn.slainc.datasource.failover;

public record DataSourceFailoverEvent(String from, String to, boolean failedOver) {
}
