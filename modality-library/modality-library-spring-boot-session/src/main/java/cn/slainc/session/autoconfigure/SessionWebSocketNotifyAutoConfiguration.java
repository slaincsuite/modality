package cn.slainc.session.autoconfigure;

import cn.slainc.session.kick.WebSocketSessionKickNotifier;
import cn.slainc.web.websocket.WebSocketBroadcaster;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(value = SessionKickAutoConfiguration.class,
        name = "cn.slainc.web.autoconfigure.WebSocketAutoConfiguration")
@ConditionalOnClass(name = "cn.slainc.web.websocket.WebSocketBroadcaster")
@ConditionalOnBean(WebSocketBroadcaster.class)
@ConditionalOnProperty(prefix = "modality.session", name = "notify.websocket-enabled", matchIfMissing = true)
public class SessionWebSocketNotifyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(WebSocketSessionKickNotifier.class)
    public WebSocketSessionKickNotifier webSocketSessionKickNotifier(WebSocketBroadcaster broadcaster) {
        return new WebSocketSessionKickNotifier(broadcaster);
    }
}
