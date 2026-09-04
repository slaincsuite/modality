package cn.slainc.monitor.autoconfigure;

import cn.slainc.monitor.metrics.HttpConcurrencyFilter;
import cn.slainc.monitor.metrics.HttpConcurrencyMetrics;
import cn.slainc.web.filter.FilterOrder;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = MonitorAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass({Filter.class, HttpConcurrencyFilter.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "modality.monitor.http", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HttpMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<HttpConcurrencyFilter> httpConcurrencyFilterRegistration(
            HttpConcurrencyMetrics metrics) {
        FilterRegistrationBean<HttpConcurrencyFilter> registration =
                new FilterRegistrationBean<>(new HttpConcurrencyFilter(metrics));
        registration.setName("httpConcurrencyFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(FilterOrder.TRACE + 10);
        return registration;
    }
}
