package cn.slainc.web.autoconfigure;

import cn.slainc.web.filter.FilterOrder;
import cn.slainc.web.timeout.RequestTimeoutConfig;
import cn.slainc.web.timeout.RequestTimeoutFilter;
import cn.slainc.web.timeout.RequestTimeoutProperties;
import cn.slainc.web.timeout.RequestTimeoutWatch;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "modality.web.timeout", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(RequestTimeoutProperties.class)
public class RequestTimeoutAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RequestTimeoutWatch.class)
    public RequestTimeoutWatch requestTimeoutWatch(RequestTimeoutProperties properties) {
        return new RequestTimeoutWatch(properties);
    }

    @Bean
    @ConditionalOnMissingBean(RequestTimeoutFilter.class)
    public FilterRegistrationBean<RequestTimeoutFilter> requestTimeoutFilter(RequestTimeoutWatch watch) {
        FilterRegistrationBean<RequestTimeoutFilter> registration =
                new FilterRegistrationBean<>(new RequestTimeoutFilter(watch));
        registration.setOrder(FilterOrder.REQUEST_TIMEOUT);
        registration.setName("requestTimeoutFilter");
        registration.addUrlPatterns("/*");
        registration.setEnabled(true);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(RequestTimeoutConfig.class)
    public RequestTimeoutConfig requestTimeoutConfig(RequestTimeoutProperties properties) {
        return new RequestTimeoutConfig(properties);
    }
}
