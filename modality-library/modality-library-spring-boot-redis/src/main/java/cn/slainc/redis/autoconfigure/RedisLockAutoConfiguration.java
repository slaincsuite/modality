package cn.slainc.redis.autoconfigure;

import cn.slainc.redis.lock.DistributedLockTemplate;
import cn.slainc.redis.lock.LockAspect;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = "modality.redis.lock", name = "enabled", matchIfMissing = true)
public class RedisLockAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient(DataRedisProperties dataRedisProperties) {
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + dataRedisProperties.getHost() + ":" + dataRedisProperties.getPort())
                .setDatabase(dataRedisProperties.getDatabase());
        if (dataRedisProperties.getPassword() != null && !dataRedisProperties.getPassword().isBlank()) {
            server.setPassword(dataRedisProperties.getPassword());
        }
        if (dataRedisProperties.getUsername() != null && !dataRedisProperties.getUsername().isBlank()) {
            server.setUsername(dataRedisProperties.getUsername());
        }
        return Redisson.create(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedLockTemplate distributedLockTemplate(RedissonClient redissonClient,
                                                           RedisProperties properties) {
        return new DistributedLockTemplate(redissonClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public LockAspect lockAspect(DistributedLockTemplate distributedLockTemplate,
                                 RedisProperties properties) {
        return new LockAspect(distributedLockTemplate, properties);
    }
}
