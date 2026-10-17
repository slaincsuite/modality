package cn.slainc.modules.upms.security;

import cn.slainc.policy.event.PolicyChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

public class PolicyPermissionCacheListener {

    private static final Logger log = LoggerFactory.getLogger(PolicyPermissionCacheListener.class);

    private final UpmsPermissionCacheInvalidator invalidator;

    public PolicyPermissionCacheListener(UpmsPermissionCacheInvalidator invalidator) {
        this.invalidator = invalidator;
    }

    @EventListener
    public void onPolicyChanged(PolicyChangedEvent event) {
        try {
            invalidator.invalidateAllPermissionCaches();
            log.info("策略 {} 发生 {} 变更，已清空权限缓存", event.policyId(), event.operation());
        } catch (RuntimeException exception) {
            log.warn("策略变更后清空权限缓存失败，缓存将在 TTL 到期后自然失效：{}", exception.getMessage());
        }
    }
}
