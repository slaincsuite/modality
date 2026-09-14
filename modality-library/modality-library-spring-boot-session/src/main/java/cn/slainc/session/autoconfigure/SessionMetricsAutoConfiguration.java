package cn.slainc.session.autoconfigure;

import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.session.metrics.SessionMetricsBinder;
import cn.slainc.session.stats.SessionStatsService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(SessionAutoConfiguration.class)
@ConditionalOnClass(name = "cn.slainc.monitor.metrics.MetricsTemplate")
@ConditionalOnBean(MetricsTemplate.class)
@ConditionalOnProperty(prefix = "modality.session", name = "metrics.enabled", matchIfMissing = true)
public class SessionMetricsAutoConfiguration {

    @Bean
    @ConditionalOnBean(SessionStatsService.class)
    @ConditionalOnMissingBean(SessionMetricsBinder.class)
    public SessionMetricsBinder sessionMetricsBinder(MetricsTemplate metricsTemplate,
                                                     SessionStatsService statsService) {
        return new SessionMetricsBinder(metricsTemplate, statsService);
    }
}
