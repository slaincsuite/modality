package cn.slainc.monitor.support;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.monitor.config.MonitorDynamicConfig;
import cn.slainc.monitor.metrics.MetricNaming;
import cn.slainc.monitor.metrics.MetricsTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;
import java.util.Optional;

public final class MonitorTestSupport {

    private MonitorTestSupport() {
    }

    public static DynamicConfigService dynamicConfigService(Map<String, String> values) {
        DynamicConfigService service = Mockito.mock(DynamicConfigService.class);
        Mockito.lenient().when(service.findString(Mockito.anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(values.get(invocation.getArgument(0))));
        return service;
    }

    public static ObjectProvider<DynamicConfigService> provider(DynamicConfigService service) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (service != null) {
            factory.addBean("dynamicConfigService", service);
        }
        return factory.getBeanProvider(DynamicConfigService.class);
    }

    public static MonitorDynamicConfig dynamicConfig(DynamicConfigService service) {
        return dynamicConfig(service, new MockEnvironment());
    }

    public static MonitorDynamicConfig dynamicConfig(DynamicConfigService service, Environment environment) {
        return new MonitorDynamicConfig(provider(service), environment, 0L);
    }

    public static MetricsTemplate metricsTemplate(MeterRegistry registry) {
        return new MetricsTemplate(registry, new MetricNaming(), true);
    }
}
