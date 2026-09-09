package cn.slainc.security.metrics;

public interface SecurityMetricsPort {

    void login(String type, boolean success);

    void authorization(boolean granted);

    void kickOut();
}
