package cn.slainc.monitor.endpoint;

import cn.slainc.monitor.autoconfigure.MonitorProperties;
import cn.slainc.monitor.metrics.BusinessMetrics;
import cn.slainc.monitor.metrics.MetricsSampler;
import cn.slainc.monitor.support.MonitorTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApplicationInfoEndpointTest {

    @Test
    @DisplayName("返回应用信息、构建信息、启动时长与业务指标")
    void shouldExposeApplicationInfo() {
        MonitorProperties properties = new MonitorProperties();
        properties.getInfo().getBusiness().put("系统名称", "modality 平台");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.setProperty("spring.application.name", "monitor-test");
        environment.setProperty("spring.application.version", "1.0.0-TEST");
        GitInfoProvider git = new GitInfoProvider("git-test.properties");
        StartupInfoRecorder recorder = new StartupInfoRecorder();
        recorder.onApplicationEvent(mock(ApplicationReadyEvent.class));
        BusinessMetrics businessMetrics = new BusinessMetrics(
                MonitorTestSupport.metricsTemplate(new SimpleMeterRegistry()),
                new MetricsSampler(MonitorTestSupport.dynamicConfig(MonitorTestSupport.dynamicConfigService(Map.of())),
                        1d));
        businessMetrics.loginSuccess("web");
        ApplicationInfoEndpoint endpoint = new ApplicationInfoEndpoint(properties, environment, git, recorder,
                provider(BusinessMetrics.class, businessMetrics), provider(cn.slainc.monitor.satoken.SaTokenMetrics.class, null),
                provider(cn.slainc.monitor.metrics.MetricsTemplate.class, null));

        Map<String, Object> info = endpoint.info();
        assertThat(info).containsKeys("application", "build", "startup", "business", "businessMetrics");
        @SuppressWarnings("unchecked")
        Map<String, Object> application = (Map<String, Object>) info.get("application");
        assertThat(application).containsEntry("name", "monitor-test").containsEntry("version", "1.0.0-TEST");
        assertThat(application.get("profiles")).isEqualTo(java.util.List.of("test"));
        @SuppressWarnings("unchecked")
        Map<String, Object> startup = (Map<String, Object>) info.get("startup");
        assertThat(startup).containsKeys("jvmStartTime", "readyTime", "uptimeMillis", "uptime", "startup");
        assertThat((Long) startup.get("uptimeMillis")).isNotNegative();
        assertThat((String) startup.get("readyTime")).isNotEqualTo("unknown");
        @SuppressWarnings("unchecked")
        Map<String, Object> build = (Map<String, Object>) info.get("build");
        assertThat(build).containsEntry("version", "9.9.9-TEST").containsEntry("artifact", "modality-boot");
        @SuppressWarnings("unchecked")
        Map<String, Object> business = (Map<String, Object>) info.get("business");
        assertThat(business).containsEntry("系统名称", "modality 平台");
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) info.get("businessMetrics");
        assertThat(metrics).containsEntry("loginSuccess", 1d);
    }

    @Test
    @DisplayName("缺少 git.properties 时版本与提交信息降级为 unknown")
    void shouldFallbackToUnknown() {
        MonitorProperties properties = new MonitorProperties();
        MockEnvironment environment = new MockEnvironment();
        GitInfoProvider git = new GitInfoProvider("git-not-exists.properties");
        ApplicationInfoEndpoint endpoint = new ApplicationInfoEndpoint(properties, environment, git,
                new StartupInfoRecorder(), provider(BusinessMetrics.class, null),
                provider(cn.slainc.monitor.satoken.SaTokenMetrics.class, null),
                provider(cn.slainc.monitor.metrics.MetricsTemplate.class, null));
        Map<String, Object> info = endpoint.info();
        @SuppressWarnings("unchecked")
        Map<String, Object> application = (Map<String, Object>) info.get("application");
        assertThat(application).containsEntry("name", "unknown").containsEntry("version", "unknown");
        @SuppressWarnings("unchecked")
        Map<String, Object> startup = (Map<String, Object>) info.get("startup");
        assertThat(startup).containsEntry("readyTime", "unknown");
        assertThat(info).doesNotContainKey("businessMetrics");
    }

    @Test
    @DisplayName("启动时长格式化输出中文单位")
    void shouldFormatHumanDuration() {
        assertThat(StartupInfoRecorder.human(0L)).isEqualTo("0秒");
        assertThat(StartupInfoRecorder.human(65_000L)).isEqualTo("1分钟5秒");
        assertThat(StartupInfoRecorder.human(3_600_000L + 120_000L)).isEqualTo("1小时2分钟0秒");
        assertThat(StartupInfoRecorder.human(-1L)).isEqualTo("0秒");
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (bean != null) {
            factory.addBean(type.getSimpleName(), bean);
        }
        return factory.getBeanProvider(type);
    }
}
