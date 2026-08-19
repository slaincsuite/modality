package cn.slainc.web.autoconfigure;

import cn.slainc.web.body.RequestBodyLimitFilter;
import cn.slainc.web.body.RequestBodyProperties;
import cn.slainc.web.filter.FilterOrder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "modality.web.request-body", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(RequestBodyProperties.class)
public class RequestBodyLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RequestBodyLimitFilter.class)
    public FilterRegistrationBean<RequestBodyLimitFilter> requestBodyLimitFilter(RequestBodyProperties properties) {
        FilterRegistrationBean<RequestBodyLimitFilter> registration =
                new FilterRegistrationBean<>(new RequestBodyLimitFilter(properties));
        registration.setOrder(FilterOrder.REQUEST_BODY);
        registration.setName("requestBodyLimitFilter");
        registration.addUrlPatterns("/*");
        registration.setEnabled(true);
        return registration;
    }
}
