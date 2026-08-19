package cn.slainc.web.autoconfigure;

import cn.slainc.web.body.RequestBodyProperties;
import cn.slainc.web.compression.CompressionProperties;
import cn.slainc.web.timeout.RequestTimeoutProperties;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class WebServerDefaultsPostProcessor implements BeanFactoryPostProcessor {

    public static final String PROPERTY_SOURCE_NAME = "modalityWebServerDefaults";

    private final Environment environment;

    public WebServerDefaultsPostProcessor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return;
        }
        Binder binder = new Binder(ConfigurationPropertySources.get(environment),
                new PropertySourcesPlaceholdersResolver(environment), ApplicationConversionService.getSharedInstance());
        Map<String, Object> defaults = new LinkedHashMap<>();
        collectCompressionDefaults(binder, defaults);
        collectRequestBodyDefaults(binder, defaults);
        collectTimeoutDefaults(binder, defaults);
        if (!defaults.isEmpty()) {
            configurableEnvironment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }

    private void collectCompressionDefaults(Binder binder, Map<String, Object> defaults) {
        CompressionProperties compression = binder.bindOrCreate("modality.web.compression", CompressionProperties.class);
        defaults.put("server.compression.enabled", compression.isEnabled());
        if (!compression.isEnabled()) {
            return;
        }
        putSize(defaults, "server.compression.min-response-size", compression.getMinResponseSize());
        if (compression.getMimeTypes() != null && !compression.getMimeTypes().isEmpty()) {
            defaults.put("server.compression.mime-types", String.join(",", compression.getMimeTypes()));
        }
        if (compression.getExcludedUserAgents() != null && !compression.getExcludedUserAgents().isEmpty()) {
            defaults.put("server.compression.excluded-user-agents",
                    String.join(",", compression.getExcludedUserAgents()));
        }
    }

    private void collectRequestBodyDefaults(Binder binder, Map<String, Object> defaults) {
        RequestBodyProperties requestBody = binder.bindOrCreate("modality.web.request-body", RequestBodyProperties.class);
        putSize(defaults, "spring.servlet.multipart.max-request-size", requestBody.getMaxRequestSize());
        putSize(defaults, "spring.servlet.multipart.max-file-size", requestBody.getMaxFileSize());
        putSize(defaults, "server.tomcat.max-http-form-post-size", requestBody.getMaxFormPostSize());
        putSize(defaults, "server.tomcat.max-swallow-size", requestBody.getMaxSwallowSize());
    }

    private void collectTimeoutDefaults(Binder binder, Map<String, Object> defaults) {
        RequestTimeoutProperties timeout = binder.bindOrCreate("modality.web.timeout", RequestTimeoutProperties.class);
        Duration connectionTimeout = timeout.getConnectionTimeout();
        if (connectionTimeout != null) {
            defaults.put("server.tomcat.connection-timeout", connectionTimeout.toMillis() + "ms");
        }
    }

    private void putSize(Map<String, Object> defaults, String key, DataSize size) {
        if (size != null) {
            defaults.put(key, size.toBytes() + "B");
        }
    }
}
