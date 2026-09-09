package cn.slainc.security.metrics;

import cn.slainc.monitor.satoken.SaTokenMetrics;

public class SaTokenMetricsAdapter implements SecurityMetricsPort {

    private final SaTokenMetrics metrics;

    public SaTokenMetricsAdapter(SaTokenMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("SaTokenMetrics 不能为空");
        }
        this.metrics = metrics;
    }

    @Override
    public void login(String type, boolean success) {
        metrics.login(type, success);
    }

    @Override
    public void authorization(boolean granted) {
        metrics.authorization(granted);
    }

    @Override
    public void kickOut() {
        metrics.kickOut();
    }
}
