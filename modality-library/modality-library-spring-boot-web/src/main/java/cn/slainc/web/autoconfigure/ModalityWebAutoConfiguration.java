package cn.slainc.web.autoconfigure;

import cn.slainc.web.async.AsyncProperties;
import cn.slainc.web.async.ModalityAsyncConfig;
import cn.slainc.web.config.ResponseWrapProperties;
import cn.slainc.web.config.TraceProperties;
import cn.slainc.web.config.WebExceptionProperties;
import cn.slainc.web.cors.CorsFilterConfig;
import cn.slainc.web.cors.CorsProperties;
import cn.slainc.web.error.ModalityErrorController;
import cn.slainc.web.exception.GlobalExceptionHandler;
import cn.slainc.web.filter.FilterOrder;
import cn.slainc.web.header.SecurityHeadersFilter;
import cn.slainc.web.header.SecurityHeadersProperties;
import cn.slainc.web.limit.ConcurrencyLimitInterceptor;
import cn.slainc.web.limit.RequestLimitProperties;
import cn.slainc.web.log.RequestLogFilter;
import cn.slainc.web.log.RequestLogProperties;
import cn.slainc.web.resource.ModalityResourceConfig;
import cn.slainc.web.resource.StaticResourceProperties;
import cn.slainc.web.result.ApiResponseAdvice;
import cn.slainc.web.serialize.ModalityJacksonCustomizer;
import cn.slainc.web.trace.TraceIdFilter;
import cn.slainc.web.trace.TraceIdGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@AutoConfigureBefore(ErrorMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({WebExceptionProperties.class, ResponseWrapProperties.class, TraceProperties.class,
        RequestLogProperties.class, CorsProperties.class, SecurityHeadersProperties.class, AsyncProperties.class,
        StaticResourceProperties.class, RequestLimitProperties.class})
public class ModalityWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.web.exception", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public GlobalExceptionHandler globalExceptionHandler(WebExceptionProperties properties) {
        return new GlobalExceptionHandler(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.web.response", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public ApiResponseAdvice apiResponseAdvice(ResponseWrapProperties properties) {
        return new ApiResponseAdvice(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.web.trace", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter(TraceProperties properties) {
        TraceIdGenerator generator = properties.isUuidStyle()
                ? TraceIdGenerator.UUID_GENERATOR
                : TraceIdGenerator.SNOWFLAKE_GENERATOR;
        return registration(new TraceIdFilter(properties, generator, FilterOrder.TRACE), FilterOrder.TRACE,
                "traceIdFilter");
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.web.cors", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public FilterRegistrationBean<CorsFilter> corsFilter(CorsProperties properties) {
        return registration(CorsFilterConfig.create(properties), FilterOrder.CORS, "corsFilter");
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.web.security-headers", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter(SecurityHeadersProperties properties) {
        return registration(new SecurityHeadersFilter(properties, FilterOrder.SECURITY_HEADERS),
                FilterOrder.SECURITY_HEADERS, "securityHeadersFilter");
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.web.request-log", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public FilterRegistrationBean<RequestLogFilter> requestLogFilter(RequestLogProperties properties) {
        return registration(new RequestLogFilter(properties, FilterOrder.REQUEST_LOG), FilterOrder.REQUEST_LOG,
                "requestLogFilter");
    }

    @Bean
    @ConditionalOnMissingBean(ErrorController.class)
    @ConditionalOnProperty(prefix = "modality.web.error-page", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public ModalityErrorController modalityErrorController() {
        return new ModalityErrorController();
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonMapperBuilderCustomizer modalityJsonMapperCustomizer() {
        return new ModalityJacksonCustomizer();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.web.async", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public ModalityAsyncConfig modalityAsyncConfig(AsyncProperties properties) {
        return new ModalityAsyncConfig(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.web.static-resource", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public ModalityResourceConfig modalityResourceConfig(StaticResourceProperties properties) {
        return new ModalityResourceConfig(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConcurrencyLimitInterceptor concurrencyLimitInterceptor(RequestLimitProperties properties) {
        return new ConcurrencyLimitInterceptor(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "modalityRequestLimitConfigurer")
    public WebMvcConfigurer modalityRequestLimitConfigurer(ConcurrencyLimitInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/**");
            }
        };
    }

    private <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> registration(T filter, int order, String name) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(order);
        registration.setName(name);
        registration.addUrlPatterns("/*");
        registration.setEnabled(true);
        return registration;
    }
}
