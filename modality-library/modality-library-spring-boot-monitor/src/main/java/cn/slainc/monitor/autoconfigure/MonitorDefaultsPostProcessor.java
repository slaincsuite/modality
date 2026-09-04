package cn.slainc.monitor.autoconfigure;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

public class MonitorDefaultsPostProcessor implements BeanFactoryPostProcessor {

    public static final String PROPERTY_SOURCE_NAME = "monitorDefaults";

    private final Environment environment;

    public MonitorDefaultsPostProcessor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        // 这些 key 用 addLast 写入，优先级最低，使用方在任何配置源里的显式取值都会覆盖
        if (enabled("modality.monitor.health.probes-enabled")) {
            defaults.put("management.endpoint.health.probes.enabled", true);
        }
        if (enabled("modality.monitor.metrics.histogram")) {
            defaults.put("management.metrics.distribution.percentiles-histogram.http.server.requests", true);
        }
        configurableEnvironment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    private boolean enabled(String key) {
        return Boolean.TRUE.equals(environment.getProperty(key, Boolean.class, Boolean.TRUE));
    }
}
