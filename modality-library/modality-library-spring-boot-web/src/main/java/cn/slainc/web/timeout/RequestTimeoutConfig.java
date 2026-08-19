package cn.slainc.web.timeout;

import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

public class RequestTimeoutConfig implements WebMvcConfigurer, Ordered {

    private final RequestTimeoutProperties properties;

    public RequestTimeoutConfig(RequestTimeoutProperties properties) {
        this.properties = properties;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        Duration asyncTimeout = properties.getAsyncTimeout();
        if (properties.isEnabled() && asyncTimeout != null) {
            configurer.setDefaultTimeout(asyncTimeout.toMillis());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
