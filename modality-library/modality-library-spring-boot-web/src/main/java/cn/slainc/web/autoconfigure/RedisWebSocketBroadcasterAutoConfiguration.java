package cn.slainc.web.autoconfigure;

import cn.slainc.web.websocket.LocalWebSocketBroadcaster;
import cn.slainc.web.websocket.RedisWebSocketBroadcaster;
import cn.slainc.web.websocket.WebSocketProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@AutoConfigureAfter(value = WebSocketAutoConfiguration.class,
        name = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnBean({LocalWebSocketBroadcaster.class, StringRedisTemplate.class})
@ConditionalOnProperty(prefix = "modality.web.websocket", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "modality.web.websocket", name = "broadcast", havingValue = "redis")
@EnableConfigurationProperties(WebSocketProperties.class)
public class RedisWebSocketBroadcasterAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(RedisWebSocketBroadcaster.class)
    public RedisWebSocketBroadcaster redisWebSocketBroadcaster(StringRedisTemplate stringRedisTemplate,
                                                               LocalWebSocketBroadcaster localWebSocketBroadcaster,
                                                               WebSocketProperties properties) {
        return new RedisWebSocketBroadcaster(stringRedisTemplate, localWebSocketBroadcaster,
                properties.getChannel());
    }
}
