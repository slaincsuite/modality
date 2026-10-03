package cn.slainc.modules.infra.autoconfigure;

import cn.slainc.modules.infra.service.CacheConsoleService;
import cn.slainc.modules.infra.service.RedisConsoleService;
import cn.slainc.modules.infra.web.CacheConsoleController;
import cn.slainc.modules.infra.web.RedisConsoleController;
import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.cache.CacheInvalidationPublisher;
import cn.slainc.redis.config.RedisDynamicConfigService;
import cn.slainc.redis.key.RedisKeyManager;
import cn.slainc.redis.monitor.RedisMonitor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(afterName = {"cn.slainc.redis.autoconfigure.RedisAutoConfiguration",
        "cn.slainc.redis.autoconfigure.RedisStructureAutoConfiguration",
        "cn.slainc.redis.autoconfigure.RedisMonitorAutoConfiguration",
        "cn.slainc.redis.autoconfigure.RedisCacheAutoConfiguration"})
@ConditionalOnBean({StringRedisTemplate.class, RedisKeyManager.class})
@ConditionalOnProperty(prefix = "modality.infra", name = "enabled", matchIfMissing = true)
@Import({RedisConsoleController.class, CacheConsoleController.class})
public class InfraRedisConsoleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisConsoleService redisConsoleService(StringRedisTemplate redis, RedisKeyManager keyManager,
                                                   ObjectProvider<RedisMonitor> monitor) {
        return new RedisConsoleService(redis, keyManager, monitor);
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheConsoleService cacheConsoleService(RedisKeyManager keyManager,
                                                   ObjectProvider<CacheInvalidationPublisher> invalidationPublisher,
                                                   ObjectProvider<RedisDynamicConfigService> configService,
                                                   RedisProperties redisProperties) {
        return new CacheConsoleService(keyManager, invalidationPublisher, configService, redisProperties);
    }
}
