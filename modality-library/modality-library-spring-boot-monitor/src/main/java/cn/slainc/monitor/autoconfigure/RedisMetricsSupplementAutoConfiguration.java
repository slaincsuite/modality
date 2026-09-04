package cn.slainc.monitor.autoconfigure;

import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.monitor.redis.RedisSupplementMetricsBinder;
import cn.slainc.redis.monitor.RedisMonitor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = MonitorAutoConfiguration.class,
        afterName = "cn.slainc.redis.autoconfigure.RedisMonitorAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(name = "cn.slainc.redis.monitor.RedisMonitor")
@ConditionalOnBean(RedisMonitor.class)
@ConditionalOnProperty(prefix = "modality.monitor.redis", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class RedisMetricsSupplementAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisSupplementMetricsBinder redisSupplementMetricsBinder(RedisMonitor redisMonitor,
                                                                     MetricsTemplate metricsTemplate,
                                                                     MonitorProperties properties) {
        MonitorProperties.Redis redis = properties.getRedis();
        return new RedisSupplementMetricsBinder(redisMonitor, metricsTemplate, redis.getInstance(),
                redis.getCacheMillis(), redis.getSlowlogCount(), redis.getSlowlogThresholdMicros());
    }
}
