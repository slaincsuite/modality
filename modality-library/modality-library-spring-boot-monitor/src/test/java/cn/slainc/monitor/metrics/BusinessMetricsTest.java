package cn.slainc.monitor.metrics;

import cn.slainc.monitor.support.MonitorTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final BusinessMetrics businessMetrics = new BusinessMetrics(MonitorTestSupport.metricsTemplate(registry),
            new MetricsSampler(MonitorTestSupport.dynamicConfig(MonitorTestSupport.dynamicConfigService(Map.of())), 1d));

    @Test
    @DisplayName("登录成功与失败分开计数，并保留类型与原因标签")
    void shouldCountLogin() {
        businessMetrics.loginSuccess("web");
        businessMetrics.loginSuccess("web");
        businessMetrics.loginFailure("web", "密码错误");
        assertThat(registry.get("modality.business.login").tag("result", "success").tag("type", "web").counter().count())
                .isEqualTo(2d);
        assertThat(registry.get("modality.business.login").tag("result", "failure").tag("type", "web")
                .tag("reason", "密码错误").counter().count()).isEqualTo(1d);
        assertThat(businessMetrics.loginSuccessCount()).isEqualTo(2d);
        assertThat(businessMetrics.loginFailureCount()).isEqualTo(1d);
    }

    @Test
    @DisplayName("注册、授权、订单、错误都按标签计数")
    void shouldCountBusinessEvents() {
        businessMetrics.register("app");
        businessMetrics.authorization(true);
        businessMetrics.authorization(false);
        businessMetrics.order("paid");
        businessMetrics.order("paid", 99.5d);
        businessMetrics.error("timeout");
        assertThat(registry.get("modality.business.register").tag("channel", "app").counter().count()).isEqualTo(1d);
        assertThat(businessMetrics.authorizationCount()).isEqualTo(2d);
        assertThat(businessMetrics.orderCount()).isEqualTo(2d);
        assertThat(businessMetrics.registerCount()).isEqualTo(1d);
        assertThat(businessMetrics.errorCount()).isEqualTo(1d);
        assertThat(registry.get("modality.business.order.amount").tag("status", "paid").summary().totalAmount())
                .isEqualTo(99.5d);
    }

    @Test
    @DisplayName("API 调用成功率与错误率可从指标反推")
    void shouldComputeApiErrorRate() {
        businessMetrics.apiCall(true);
        businessMetrics.apiCall(true);
        businessMetrics.apiCall(false);
        assertThat(businessMetrics.apiCallCount()).isEqualTo(3d);
        assertThat(businessMetrics.apiErrorRate()).isCloseTo(1d / 3d, org.assertj.core.data.Offset.offset(1.0E-9));
    }

    @Test
    @DisplayName("采样率为 0 时不再记录高频指标")
    void shouldRespectSamplingRate() {
        BusinessMetrics disabled = new BusinessMetrics(MonitorTestSupport.metricsTemplate(registry),
                new MetricsSampler(MonitorTestSupport.dynamicConfig(MonitorTestSupport.dynamicConfigService(Map.of())),
                        0d));
        disabled.apiCall(true);
        disabled.error("boom");
        assertThat(disabled.apiCallCount()).isZero();
        assertThat(disabled.errorCount()).isZero();
        disabled.loginSuccess("web");
        assertThat(disabled.loginSuccessCount()).isEqualTo(1d);
    }

    @Test
    @DisplayName("快照返回全部业务计数")
    void shouldReturnSnapshot() {
        businessMetrics.loginSuccess();
        businessMetrics.register();
        Map<String, Double> snapshot = businessMetrics.snapshot();
        assertThat(snapshot).containsEntry("loginSuccess", 1d).containsEntry("register", 1d)
                .containsKeys("loginFailure", "authorization", "apiCall", "apiErrorRate", "error", "order");
    }

    @Test
    @DisplayName("空标签值降级为中文未知")
    void shouldFallbackBlankTag() {
        businessMetrics.register(null);
        assertThat(registry.get("modality.business.register").tag("channel", "未知").counter().count()).isEqualTo(1d);
    }
}
