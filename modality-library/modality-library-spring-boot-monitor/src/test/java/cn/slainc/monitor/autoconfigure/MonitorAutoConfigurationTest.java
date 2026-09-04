package cn.slainc.monitor.autoconfigure;

import cn.slainc.monitor.alert.AlertService;
import cn.slainc.monitor.db.DatabaseMetrics;
import cn.slainc.monitor.endpoint.ApplicationInfoEndpoint;
import cn.slainc.monitor.metrics.BusinessMetrics;
import cn.slainc.monitor.metrics.HttpConcurrencyMetrics;
import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.monitor.system.OshiSystemMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MonitorAutoConfiguration.class, AlertAutoConfiguration.class,
                    MonitorHealthAutoConfiguration.class, ApplicationInfoEndpointAutoConfiguration.class,
                    OshiSystemMetricsAutoConfiguration.class, DruidPoolMetricsAutoConfiguration.class,
                    RedisMetricsSupplementAutoConfiguration.class,
                    SaTokenMetricsAutoConfiguration.class, DataSourceAutoConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withClassLoader(new FilteredClassLoader("com.zaxxer.hikari", "org.h2", "com.alibaba.druid",
                    "cn.slainc.redis", "cn.dev33.satoken"));

    @Test
    @DisplayName("没有 DataSource 与 Redis 时监控自动配置照样装配，连接池与 Redis 绑定器自动退避")
    void shouldStartWithoutInfrastructure() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DataSource.class);
            assertThat(context).hasSingleBean(MetricsTemplate.class);
            assertThat(context).hasSingleBean(BusinessMetrics.class);
            assertThat(context).hasSingleBean(HttpConcurrencyMetrics.class);
            assertThat(context).hasSingleBean(DatabaseMetrics.class);
            assertThat(context).hasSingleBean(OshiSystemMetricsBinder.class);
            assertThat(context).hasSingleBean(AlertService.class);
            assertThat(context).hasBean("businessHealthIndicator");
            assertThat(context).hasSingleBean(ApplicationInfoEndpoint.class);
            assertThat(context).doesNotHaveBean("druidPoolMetricsBinder");
            assertThat(context).doesNotHaveBean("redisSupplementMetricsBinder");
            assertThat(context).doesNotHaveBean("saTokenMetricsBinder");
        });
    }

    @Test
    @DisplayName("业务指标门面写入的计数器能在注册表里查到")
    void shouldRecordBusinessMetrics() {
        runner.run(context -> {
            BusinessMetrics businessMetrics = context.getBean(BusinessMetrics.class);
            businessMetrics.loginSuccess("web");
            businessMetrics.loginSuccess("web");
            businessMetrics.order("paid", 12.5d);
            MeterRegistry registry = context.getBean(MeterRegistry.class);
            assertThat(registry.get("modality.business.login").tag("result", "success").tag("type", "web")
                    .counter().count()).isEqualTo(2d);
            assertThat(registry.get("modality.business.order").tag("status", "paid").counter().count())
                    .isEqualTo(1d);
        });
    }

    @Test
    @DisplayName("modality.monitor.enabled=false 时所有监控 Bean 退避")
    void shouldBackOffWhenDisabled() {
        runner.withPropertyValues("modality.monitor.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MetricsTemplate.class);
            assertThat(context).doesNotHaveBean(BusinessMetrics.class);
            assertThat(context).doesNotHaveBean(AlertService.class);
        });
    }

    @Test
    @DisplayName("告警默认规则在无基础设施时也能装载")
    void shouldLoadDefaultAlertRules() {
        runner.run(context -> {
            AlertService alertService = context.getBean(AlertService.class);
            assertThat(alertService.rules()).hasSize(5);
            assertThat(context).hasSingleBean(cn.slainc.monitor.alert.AlertEvaluationScheduler.class);
        });
    }

    @Test
    @DisplayName("健康指示器在未配置业务检查时返回 UP")
    void shouldExposeBusinessHealth() {
        runner.run(context -> {
            org.springframework.boot.health.contributor.HealthIndicator indicator =
                    context.getBean("businessHealthIndicator",
                            org.springframework.boot.health.contributor.HealthIndicator.class);
            assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
            assertThat(indicator.health().getDetails()).containsEntry("checks", "未配置业务健康检查");
        });
    }

    @Test
    @DisplayName("业务健康检查由使用方扩展，异常时降级为 DOWN")
    void shouldAggregateBusinessChecks() {
        runner.withBean("failedCheck", cn.slainc.monitor.health.BusinessHealthCheck.class, () -> new cn.slainc.monitor.health.BusinessHealthCheck() {
            @Override
            public String name() {
                return "订单积压";
            }

            @Override
            public org.springframework.boot.health.contributor.Health health() {
                return org.springframework.boot.health.contributor.Health.down().withDetail("待处理", 42).build();
            }
        }).run(context -> {
            org.springframework.boot.health.contributor.HealthIndicator indicator =
                    context.getBean("businessHealthIndicator",
                            org.springframework.boot.health.contributor.HealthIndicator.class);
            assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
            assertThat(indicator.health().getDetails().get("订单积压")).asString().contains("待处理");
        });
    }
}
