package cn.slainc.openapi.support;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.openapi.event.ConsentGrantedEvent;
import cn.slainc.openapi.event.ConsentRevokedEvent;
import cn.slainc.openapi.model.App;
import cn.slainc.openapi.model.AppStatus;
import cn.slainc.openapi.model.ConsentRecord;
import cn.slainc.openapi.spi.AppStore;
import cn.slainc.openapi.spi.ConsentService;
import cn.slainc.openapi.spi.ConsentStore;
import cn.slainc.openapi.spi.ScopeStore;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DefaultConsentService implements ConsentService {

    private final ConsentStore store;

    private final AppStore appStore;

    private final ScopeStore scopeStore;

    private final ApplicationEventPublisher eventPublisher;

    public DefaultConsentService(ConsentStore store, AppStore appStore, ScopeStore scopeStore,
                                 ApplicationEventPublisher eventPublisher) {
        this.store = store;
        this.appStore = appStore;
        this.scopeStore = scopeStore;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ConsentRecord grant(String userId, String appId, List<String> scopes, String source) {
        requireApp(appId);
        validateScopes(scopes);
        ConsentRecord record = store.find(userId, appId).filter(ConsentRecord::active)
                .orElseGet(() -> ConsentRecord.create(userId, appId, scopes, source));
        record.mergeScopes(scopes);
        ConsentRecord saved = store.save(record);
        publish(new ConsentGrantedEvent(userId, appId, saved.getScopes(), System.currentTimeMillis()));
        return saved;
    }

    @Override
    public ConsentRecord revoke(String userId, String appId) {
        ConsentRecord record = requireActive(userId, appId);
        record.setRevokedAt(System.currentTimeMillis());
        ConsentRecord saved = store.save(record);
        publish(new ConsentRevokedEvent(userId, appId, System.currentTimeMillis()));
        return saved;
    }

    @Override
    public ConsentRecord revokeScope(String userId, String appId, String scope) {
        ConsentRecord record = requireActive(userId, appId);
        List<String> remaining = new ArrayList<>(record.getScopes());
        if (!remaining.remove(scope)) {
            throw new BizException(ResultCode.DATA_NOT_FOUND,
                    "授权记录中不存在 Scope " + scope + "，无法单独撤销");
        }
        record.setScopes(remaining);
        if (remaining.isEmpty()) {
            record.setRevokedAt(System.currentTimeMillis());
            publish(new ConsentRevokedEvent(userId, appId, System.currentTimeMillis()));
        }
        return store.save(record);
    }

    @Override
    public boolean isGranted(String userId, String appId, String scope) {
        return store.find(userId, appId)
                .filter(ConsentRecord::active)
                .filter(record -> record.getScopes().contains(scope))
                .isPresent();
    }

    @Override
    public List<ConsentRecord> listByUser(String userId) {
        return store.listByUser(userId);
    }

    @Override
    public List<ConsentRecord> listByApp(String appId) {
        return store.listByApp(appId);
    }

    private void requireApp(String appId) {
        Optional<App> found = appStore.find(appId);
        App app = found.orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND, "应用 " + appId + " 不存在"));
        if (app.getStatus() != AppStatus.ONLINE) {
            throw new BizException(ResultCode.DATA_STATE_INVALID,
                    "应用 " + appId + " 当前状态「" + app.getStatus().label() + "」，仅已上线应用可授权");
        }
    }

    private void validateScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new BizException(ResultCode.PARAM_MISSING, "授权 Scope 列表不能为空");
        }
        for (String scope : scopes) {
            if (scopeStore.find(scope).isEmpty()) {
                throw new BizException(ResultCode.DATA_NOT_FOUND, "Scope " + scope + " 未在平台登记");
            }
        }
    }

    private ConsentRecord requireActive(String userId, String appId) {
        ConsentRecord record = store.find(userId, appId)
                .orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND,
                        "用户 " + userId + " 对应用 " + appId + " 没有授权记录"));
        if (!record.active()) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "该授权已被撤销，不能重复操作");
        }
        return record;
    }

    private void publish(Object event) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(event);
        }
    }
}
