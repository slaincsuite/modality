package cn.slainc.security.autoconfigure;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.security.config.SecurityProperties;
import cn.slainc.security.metrics.SecurityCounters;
import cn.slainc.security.metrics.SecurityMetricsPort;
import cn.slainc.security.web.SecurityExceptionHandler;
import cn.slainc.security.web.SecurityFilterFactory;
import cn.slainc.security.web.UserContextBindingFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@AutoConfigureAfter(SecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "modality.security", name = "web.enabled", matchIfMissing = true)
public class SecurityWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "securitySaServletFilterRegistration")
    public FilterRegistrationBean<Filter> securitySaServletFilterRegistration(
            SecurityProperties properties, SecurityConfigReader config,
            ObjectProvider<SecurityMetricsPort> metrics) {
        return SecurityFilterFactory.create(properties, config, metrics.getIfAvailable(SecurityCounters::new));
    }

    @Bean
    @ConditionalOnMissingBean(name = "userContextBindingFilterRegistration")
    @ConditionalOnProperty(prefix = "modality.security", name = "user-context.enabled", matchIfMissing = true)
    public FilterRegistrationBean<UserContextBindingFilter> userContextBindingFilterRegistration(
            ObjectProvider<cn.dev33.satoken.stp.StpInterface> stpInterface) {
        FilterRegistrationBean<UserContextBindingFilter> registration =
                new FilterRegistrationBean<>(new UserContextBindingFilter(stpInterface));
        // 紧随 Sa-Token 安全过滤器(-6000)之后，保证业务过滤器与端点读到的用户上下文已就绪
        registration.setOrder(-5900);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(name = "securityCookieCustomizer")
    public SecurityCookieCustomizer securityCookieCustomizer(SaTokenConfig saTokenConfig,
                                                             SecurityProperties properties) {
        return new SecurityCookieCustomizer(saTokenConfig, properties);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityExceptionHandler.class)
    @ConditionalOnProperty(prefix = "modality.security.web", name = "exception-handler-enabled",
            matchIfMissing = true)
    public SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "cn.dev33.satoken.interceptor.SaInterceptor")
    public static class SecurityAnnotationInterceptorConfig implements WebMvcConfigurer {

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new SaInterceptor(handler -> {
            })).addPathPatterns("/**");
        }
    }
}
