package cn.slainc.web.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "modality.web.server-defaults", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class WebServerDefaultsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(WebServerDefaultsPostProcessor.class)
    public static WebServerDefaultsPostProcessor webServerDefaultsPostProcessor(Environment environment) {
        return new WebServerDefaultsPostProcessor(environment);
    }
}
