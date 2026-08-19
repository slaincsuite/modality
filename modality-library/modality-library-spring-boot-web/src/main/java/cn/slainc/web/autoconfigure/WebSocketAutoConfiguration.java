package cn.slainc.web.autoconfigure;

import cn.slainc.web.websocket.LocalWebSocketBroadcaster;
import cn.slainc.web.websocket.TokenWebSocketAuthenticator;
import cn.slainc.web.websocket.WebSocketAuthenticator;
import cn.slainc.web.websocket.WebSocketBroadcaster;
import cn.slainc.web.websocket.WebSocketHandshakeInterceptor;
import cn.slainc.web.websocket.WebSocketHeartbeat;
import cn.slainc.web.websocket.WebSocketMessageHandler;
import cn.slainc.web.websocket.WebSocketMessageListener;
import cn.slainc.web.websocket.WebSocketProperties;
import cn.slainc.web.websocket.WebSocketSessionRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import java.util.List;
import java.util.Map;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({WebSocketHandler.class, WebSocketConfigurer.class})
@ConditionalOnProperty(prefix = "modality.web.websocket", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(WebSocketSessionRegistry.class)
    public WebSocketSessionRegistry webSocketSessionRegistry() {
        return new WebSocketSessionRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(WebSocketAuthenticator.class)
    public TokenWebSocketAuthenticator tokenWebSocketAuthenticator(WebSocketProperties properties) {
        return new TokenWebSocketAuthenticator(properties);
    }

    @Bean
    @ConditionalOnMissingBean(WebSocketHandshakeInterceptor.class)
    public WebSocketHandshakeInterceptor webSocketHandshakeInterceptor(WebSocketAuthenticator authenticator) {
        return new WebSocketHandshakeInterceptor(authenticator);
    }

    @Bean
    @ConditionalOnMissingBean(WebSocketMessageHandler.class)
    public WebSocketMessageHandler webSocketMessageHandler(WebSocketSessionRegistry registry,
                                                           ObjectProvider<WebSocketMessageListener> listeners) {
        return new WebSocketMessageHandler(registry, listeners);
    }

    @Bean
    @ConditionalOnMissingBean(WebSocketBroadcaster.class)
    public LocalWebSocketBroadcaster localWebSocketBroadcaster(WebSocketSessionRegistry registry) {
        return new LocalWebSocketBroadcaster(registry);
    }

    @Bean
    @ConditionalOnMissingBean(WebSocketHandlerMapping.class)
    public WebSocketHandlerMapping defaultWebSocketHandlerMapping(WebSocketMessageHandler handler,
                                                                  WebSocketHandshakeInterceptor interceptor,
                                                                  WebSocketProperties properties) {
        WebSocketHttpRequestHandler requestHandler = new WebSocketHttpRequestHandler(handler);
        List<HandshakeInterceptor> interceptors = List.of(interceptor,
                new OriginHandshakeInterceptor(properties.getAllowedOrigins()));
        requestHandler.setHandshakeInterceptors(interceptors);
        WebSocketHandlerMapping mapping = new WebSocketHandlerMapping();
        mapping.setUrlMap(Map.of(properties.getPath(), requestHandler));
        mapping.setOrder(1);
        return mapping;
    }

    @Bean
    @ConditionalOnMissingBean(WebSocketHeartbeat.class)
    @ConditionalOnProperty(prefix = "modality.web.websocket", name = "heartbeat-enabled", havingValue = "true",
            matchIfMissing = true)
    public WebSocketHeartbeat webSocketHeartbeat(WebSocketSessionRegistry registry, WebSocketProperties properties) {
        return new WebSocketHeartbeat(registry, properties);
    }
}
