package cn.slainc.redis.autoconfigure;

import cn.slainc.redis.monitor.RedisMonitor;
import cn.slainc.redis.monitor.RedisMonitorMetricsBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnProperty(prefix = "modality.redis.monitor", name = "enabled", matchIfMissing = true)
public class RedisMonitorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisMonitor redisMonitor(StringRedisTemplate stringRedisTemplate, RedisProperties properties) {
        return new RedisMonitor(stringRedisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisMonitorMetricsBinder redisMonitorMetricsBinder(RedisMonitor redisMonitor,
                                                               RedisProperties properties) {
        return new RedisMonitorMetricsBinder(redisMonitor, properties.getMonitor().getInstance());
    }
}
