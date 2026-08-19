package cn.slainc.web.autoconfigure;

import cn.slainc.web.websocket.SaTokenWebSocketAuthenticator;
import cn.slainc.web.websocket.WebSocketAuthenticator;
import cn.slainc.web.websocket.WebSocketProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureBefore(WebSocketAutoConfiguration.class)
@ConditionalOnClass(name = "cn.dev33.satoken.stp.StpUtil")
@ConditionalOnProperty(prefix = "modality.web.websocket", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WebSocketProperties.class)
public class SaTokenWebSocketAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(WebSocketAuthenticator.class)
    public SaTokenWebSocketAuthenticator saTokenWebSocketAuthenticator(WebSocketProperties properties) {
        return new SaTokenWebSocketAuthenticator(properties);
    }
}
