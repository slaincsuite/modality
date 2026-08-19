package cn.slainc.web.autoconfigure;

import cn.slainc.web.doc.OpenApiDisabledFilter;
import cn.slainc.web.filter.FilterOrder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "modality.web.open-api", name = "enabled", havingValue = "false")
public class OpenApiDisabledAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OpenApiDisabledFilter.class)
    public FilterRegistrationBean<OpenApiDisabledFilter> openApiDisabledFilter() {
        FilterRegistrationBean<OpenApiDisabledFilter> registration =
                new FilterRegistrationBean<>(new OpenApiDisabledFilter());
        registration.setOrder(FilterOrder.DOC_GUARD);
        registration.setName("openApiDisabledFilter");
        registration.addUrlPatterns("/*");
        registration.setEnabled(true);
        return registration;
    }
}
