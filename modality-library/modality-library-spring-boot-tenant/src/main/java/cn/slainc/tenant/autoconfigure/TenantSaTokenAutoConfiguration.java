package cn.slainc.tenant.autoconfigure;

import cn.slainc.tenant.resolver.SessionTenantResolver;
import cn.slainc.tenant.resolver.TenantLookup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "cn.dev33.satoken.stp.StpUtil")
@ConditionalOnProperty(prefix = "modality.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TenantProperties.class)
public class TenantSaTokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.tenant.resolver.session", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public SessionTenantResolver sessionTenantResolver(TenantProperties properties,
                                                       ObjectProvider<TenantLookup> lookup) {
        return new SessionTenantResolver(properties.getResolver().getSession().getAttribute(), lookup.getIfAvailable());
    }
}
