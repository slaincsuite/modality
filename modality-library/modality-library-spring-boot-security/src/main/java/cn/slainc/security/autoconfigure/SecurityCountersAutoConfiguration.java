package cn.slainc.security.autoconfigure;

import cn.slainc.security.metrics.SecurityCounters;
import cn.slainc.security.metrics.SecurityMetricsPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(SecurityMetricsAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.security.metrics", name = "enabled", matchIfMissing = true)
public class SecurityCountersAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityMetricsPort.class)
    public SecurityMetricsPort securityCounters() {
        return new SecurityCounters();
    }
}
