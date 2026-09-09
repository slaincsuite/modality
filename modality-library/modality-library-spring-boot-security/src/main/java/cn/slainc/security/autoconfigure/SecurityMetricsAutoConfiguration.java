package cn.slainc.security.autoconfigure;

import cn.slainc.monitor.satoken.SaTokenMetrics;
import cn.slainc.security.metrics.SaTokenMetricsAdapter;
import cn.slainc.security.metrics.SecurityCounters;
import cn.slainc.security.metrics.SecurityMetricsPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "cn.slainc.monitor.satoken.SaTokenMetrics")
@ConditionalOnProperty(prefix = "modality.security.metrics", name = "enabled", matchIfMissing = true)
public class SecurityMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityMetricsPort.class)
    public SecurityMetricsPort saTokenMetricsAdapter(ObjectProvider<SaTokenMetrics> metrics) {
        SaTokenMetrics saTokenMetrics = metrics.getIfAvailable();
        return saTokenMetrics == null ? new SecurityCounters() : new SaTokenMetricsAdapter(saTokenMetrics);
    }
}
