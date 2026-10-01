package cn.slainc.modules.open.listener;

import cn.slainc.core.event.ConsentGrantedEvent;
import cn.slainc.core.event.ConsentRevokedEvent;
import cn.slainc.openapi.spi.CredentialStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import java.util.LinkedHashSet;

// 开放平台授权事件桥：open-api 领域事件转发为 core 事件，供审计中心等下游统一监听（批次 11 关键决策）
public class OpenEventBridge {

    private static final Logger log = LoggerFactory.getLogger(OpenEventBridge.class);

    private final CredentialStore credentialStore;

    private final ApplicationEventPublisher publisher;

    public OpenEventBridge(CredentialStore credentialStore, ApplicationEventPublisher publisher) {
        this.credentialStore = credentialStore;
        this.publisher = publisher;
    }

    @EventListener
    public void onGranted(cn.slainc.openapi.event.ConsentGrantedEvent event) {
        Long userId = parseUserId(event.userId());
        try {
            publisher.publishEvent(new ConsentGrantedEvent(userId, clientIdOf(event.appId()),
                    new LinkedHashSet<>(event.scopes())));
        } catch (RuntimeException e) {
            log.warn("转发授权事件失败: {}", e.getMessage());
        }
    }

    @EventListener
    public void onRevoked(cn.slainc.openapi.event.ConsentRevokedEvent event) {
        Long userId = parseUserId(event.userId());
        try {
            publisher.publishEvent(new ConsentRevokedEvent(userId, clientIdOf(event.appId()), "开放平台解约"));
        } catch (RuntimeException e) {
            log.warn("转发解约事件失败: {}", e.getMessage());
        }
    }

    private String clientIdOf(String appId) {
        return credentialStore.find(appId).map(credential -> credential.getClientId() == null
                ? appId : credential.getClientId()).orElse(appId);
    }

    private Long parseUserId(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
