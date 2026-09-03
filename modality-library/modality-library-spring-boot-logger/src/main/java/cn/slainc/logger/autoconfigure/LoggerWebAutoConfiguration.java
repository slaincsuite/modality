package cn.slainc.logger.autoconfigure;

import cn.slainc.logger.context.LogContextFilter;
import cn.slainc.logger.context.LoginIdResolver;
import cn.slainc.logger.trace.SpanFilter;
import cn.slainc.logger.trace.SpanIdGenerator;
import cn.slainc.logger.trace.TracePropagationInterceptor;
import cn.slainc.web.filter.FilterOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "modality.logger", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoggerWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SpanIdGenerator.class)
    @ConditionalOnProperty(prefix = "modality.logger.trace", name = "span-enabled", havingValue = "true",
            matchIfMissing = true)
    public SpanIdGenerator spanIdGenerator(LoggerProperties properties) {
        return SpanIdGenerator.of(properties.getTrace().getSpanIdStyle());
    }

    @Bean
    @ConditionalOnMissingBean(SpanFilter.class)
    @ConditionalOnProperty(prefix = "modality.logger.trace", name = "span-enabled", havingValue = "true",
            matchIfMissing = true)
    public FilterRegistrationBean<SpanFilter> spanFilter(LoggerProperties properties,
                                                         SpanIdGenerator spanIdGenerator) {
        FilterRegistrationBean<SpanFilter> registration = new FilterRegistrationBean<>(
                new SpanFilter(properties.getTrace(), spanIdGenerator, FilterOrder.TRACE + 1));
        registration.setName("spanFilter");
        registration.setOrder(FilterOrder.TRACE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(LogContextFilter.class)
    @ConditionalOnProperty(prefix = "modality.logger.context", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public FilterRegistrationBean<LogContextFilter> logContextFilter(LoggerProperties properties,
                                                                     ObjectProvider<LoginIdResolver> loginIdResolver) {
        LogContextFilter filter = new LogContextFilter(properties.getContext(),
                loginIdResolver.getIfAvailable(() -> LoginIdResolver.USER_CONTEXT), FilterOrder.SA_TOKEN + 500);
        FilterRegistrationBean<LogContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("logContextFilter");
        registration.setOrder(FilterOrder.SA_TOKEN + 500);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(TracePropagationInterceptor.class)
    @ConditionalOnProperty(prefix = "modality.logger.trace", name = "propagate", havingValue = "true",
            matchIfMissing = true)
    public TracePropagationInterceptor tracePropagationInterceptor() {
        return new TracePropagationInterceptor();
    }
}
