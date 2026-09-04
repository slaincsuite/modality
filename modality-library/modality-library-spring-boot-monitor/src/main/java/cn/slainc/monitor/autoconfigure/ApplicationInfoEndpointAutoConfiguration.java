package cn.slainc.monitor.autoconfigure;

import cn.slainc.monitor.endpoint.ApplicationInfoContributor;
import cn.slainc.monitor.endpoint.ApplicationInfoEndpoint;
import cn.slainc.monitor.endpoint.GitInfoProvider;
import cn.slainc.monitor.endpoint.StartupInfoRecorder;
import cn.slainc.monitor.metrics.BusinessMetrics;
import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.monitor.satoken.SaTokenMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration(after = MonitorAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(Endpoint.class)
@ConditionalOnProperty(prefix = "modality.monitor.info", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ApplicationInfoEndpointAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GitInfoProvider gitInfoProvider() {
        return new GitInfoProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public StartupInfoRecorder startupInfoRecorder() {
        return new StartupInfoRecorder();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApplicationInfoEndpoint applicationInfoEndpoint(MonitorProperties properties, Environment environment,
                                                          GitInfoProvider gitInfoProvider,
                                                          StartupInfoRecorder startupInfoRecorder,
                                                          ObjectProvider<BusinessMetrics> businessMetrics,
                                                          ObjectProvider<SaTokenMetrics> saTokenMetrics,
                                                          ObjectProvider<MetricsTemplate> metricsTemplate) {
        return new ApplicationInfoEndpoint(properties, environment, gitInfoProvider, startupInfoRecorder,
                businessMetrics, saTokenMetrics, metricsTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(name = "applicationInfoContributor")
    public ApplicationInfoContributor applicationInfoContributor(MonitorProperties properties,
                                                                 GitInfoProvider gitInfoProvider,
                                                                 StartupInfoRecorder startupInfoRecorder) {
        return new ApplicationInfoContributor(properties, gitInfoProvider, startupInfoRecorder);
    }
}
