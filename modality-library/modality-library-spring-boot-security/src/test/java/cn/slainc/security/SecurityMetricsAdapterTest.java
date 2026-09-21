package cn.slainc.security;

import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.monitor.satoken.SaTokenMetrics;
import cn.slainc.security.metrics.SaTokenMetricsAdapter;
import cn.slainc.security.metrics.SecurityCounters;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityMetricsAdapterTest {

    @Test
    void adapterForwardsLoginToSaTokenMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SaTokenMetrics metrics = new SaTokenMetrics(new MetricsTemplate(registry, new cn.slainc.monitor.metrics.MetricNaming("modality"), false));
        SaTokenMetricsAdapter adapter = new SaTokenMetricsAdapter(metrics);
        adapter.login("TENANT_USER", true);
        adapter.login("TENANT_USER", false);
        adapter.login("PLATFORM_ADMIN", true);
        assertThat(metrics.loginCount()).isEqualTo(3.0);
        io.micrometer.core.instrument.Counter userSuccess = registry.find("modality.satoken.login")
                .tag("type", "TENANT_USER").tag("result", "success").counter();
        assertThat(userSuccess).isNotNull();
        assertThat(userSuccess.count()).isEqualTo(1d);
    }

    @Test
    void adapterForwardsAuthorizationAndKickOut() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SaTokenMetrics metrics = new SaTokenMetrics(new MetricsTemplate(registry, new cn.slainc.monitor.metrics.MetricNaming("modality"), false));
        SaTokenMetricsAdapter adapter = new SaTokenMetricsAdapter(metrics);
        adapter.authorization(true);
        adapter.authorization(true);
        adapter.authorization(false);
        adapter.kickOut();
        assertThat(metrics.authorizationCount()).isEqualTo(3.0);
        assertThat(metrics.kickOutCount()).isEqualTo(1.0);
    }

    @Test
    void countersNoopAccumulatesReadableNumbers() {
        SecurityCounters counters = new SecurityCounters();
        counters.login("TENANT_USER", true);
        counters.login("TENANT_USER", false);
        counters.authorization(true);
        counters.authorization(false);
        counters.authorization(false);
        counters.kickOut();
        assertThat(counters.loginSuccess()).isEqualTo(1);
        assertThat(counters.loginFailure()).isEqualTo(1);
        assertThat(counters.authorizationGranted()).isEqualTo(1);
        assertThat(counters.authorizationDenied()).isEqualTo(2);
        assertThat(counters.kickOutCount()).isEqualTo(1);
        Map<String, Long> snapshot = counters.snapshot();
        assertThat(snapshot).containsEntry("loginSuccess", 1L).containsEntry("kickOut", 1L);
    }

    @Test
    void adapterRejectsNullMetrics() {
        assertThat(new SecurityCounters()).isNotNull();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SaTokenMetricsAdapter(null));
    }
}
