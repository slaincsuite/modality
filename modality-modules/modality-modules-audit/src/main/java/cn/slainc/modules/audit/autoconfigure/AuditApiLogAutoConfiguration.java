package cn.slainc.modules.audit.autoconfigure;

import cn.slainc.modules.audit.filter.ApiLogFilter;
import cn.slainc.modules.audit.writer.ApiLogWriter;
import cn.slainc.modules.audit.config.AuditConfigReader;
import cn.slainc.modules.audit.mapper.ApiLogMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "modality.audit.api-log", name = "enabled", matchIfMissing = true)
public class AuditApiLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApiLogWriter apiLogWriter(ApiLogMapper apiLogMapper, AuditProperties properties) {
        return new ApiLogWriter(apiLogMapper, properties.getApiLog().getQueueCapacity());
    }

    @Bean
    @ConditionalOnMissingBean(ApiLogFilter.class)
    public FilterRegistrationBean<ApiLogFilter> apiLogFilter(ApiLogWriter writer, AuditConfigReader configReader,
                                                             AuditProperties properties) {
        FilterRegistrationBean<ApiLogFilter> registration = new FilterRegistrationBean<>(
                new ApiLogFilter(writer, configReader, properties.getApiLog().getExcludePaths()));
        registration.setOrder(properties.getApiLog().getFilterOrder());
        return registration;
    }
}
