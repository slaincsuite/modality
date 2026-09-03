package cn.slainc.monitor.satoken;

public interface SaTokenMetricsSource {

    long onlineUsers();

    long tokenCount();
}
