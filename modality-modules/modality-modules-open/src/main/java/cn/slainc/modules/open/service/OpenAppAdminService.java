package cn.slainc.modules.open.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.store.JdbcAppLogStore;
import cn.slainc.openapi.domain.App;
import cn.slainc.openapi.domain.AppCredential;
import cn.slainc.openapi.domain.AppStatus;
import cn.slainc.openapi.spi.AppService;
import cn.slainc.openapi.spi.AppStore;
import cn.slainc.openapi.spi.ConsentService;
import cn.slainc.openapi.spi.CredentialService;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 管理端应用治理：分页检索、审核、上下线、删除（级联解约）与生命周期日志
public class OpenAppAdminService {

    private final AppService appService;

    private final AppStore appStore;

    private final CredentialService credentialService;

    private final ConsentService consentService;

    private final ObjectProvider<JdbcAppLogStore> appLogStore;

    public OpenAppAdminService(AppService appService, AppStore appStore, CredentialService credentialService,
                               ConsentService consentService, ObjectProvider<JdbcAppLogStore> appLogStore) {
        this.appService = appService;
        this.appStore = appStore;
        this.credentialService = credentialService;
        this.consentService = consentService;
        this.appLogStore = appLogStore;
    }

    public PageResult<App> page(String keyword, String category, String appType, String status, String ownerId,
                                long current, long size) {
        List<App> matched = appService.search(keyword, category, null).stream()
                .filter(app -> appType == null || appType.isBlank()
                        || app.getAppType().name().equalsIgnoreCase(appType.trim()))
                .filter(app -> status == null || status.isBlank()
                        || app.getStatus().name().equalsIgnoreCase(status.trim()))
                .filter(app -> ownerId == null || ownerId.isBlank() || ownerId.trim().equals(app.getOwnerId()))
                .toList();
        return PageResult.from(matched, current, size);
    }

    public Map<String, Object> detail(String appId) {
        App app = appService.get(appId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app", app);
        result.put("credential", findCredentialQuietly(appId));
        result.put("logs", logs(appId, 20));
        return result;
    }

    // 库的 CredentialService.find 在未签发时直接抛业务异常，详情侧静默置空
    private AppCredential findCredentialQuietly(String appId) {
        try {
            return credentialService.find(appId);
        } catch (BizException e) {
            return null;
        }
    }

    public App approve(String appId, String reason, String operator) {
        App app = appService.approve(appId);
        log(appId, "APPROVE", operator, reason, null);
        return app;
    }

    public App reject(String appId, String reason, String operator) {
        App app = appService.reject(appId);
        log(appId, "REJECT", operator, reason, null);
        return app;
    }

    public App offline(String appId, String reason, String operator) {
        App app = appService.offline(appId);
        log(appId, "OFFLINE", operator, reason, null);
        return app;
    }

    public App republish(String appId, String operator) {
        App app = appService.republish(appId);
        log(appId, "REPUBLISH", operator, null, null);
        return app;
    }

    // 删除仅允许草稿与已下线应用：软删应用本体，禁用凭证并撤销全部活跃授权（级联解约）
    public Map<String, Object> delete(String appId, String operator) {
        App app = appService.get(appId);
        if (app.getStatus() != AppStatus.DRAFT && app.getStatus() != AppStatus.OFFLINE) {
            throw new BizException(ResultCode.DATA_STATE_INVALID,
                    "仅草稿与已下线应用可删除，请先下线「" + app.getName() + "」");
        }
        int revokedConsents = 0;
        for (var record : consentService.listByApp(appId)) {
            if (record.active()) {
                consentService.revoke(record.getUserId(), appId);
                revokedConsents++;
            }
        }
        if (findCredentialQuietly(appId) != null) {
            credentialService.disable(appId);
        }
        appStore.delete(appId);
        log(appId, "DELETE", operator, null, "级联解约 " + revokedConsents + " 条授权");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appId", appId);
        result.put("revokedConsents", revokedConsents);
        return result;
    }

    public List<Object> logs(String appId, int limit) {
        JdbcAppLogStore store = appLogStore.getIfAvailable();
        if (store == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "应用日志存储不可用（缺少 JdbcTemplate）");
        }
        return List.copyOf(store.listByApp(appId, limit));
    }

    void log(String appId, String action, String operator, String reason, String detail) {
        JdbcAppLogStore store = appLogStore.getIfAvailable();
        if (store != null) {
            store.append(appId, action, operator, reason, detail);
        }
    }
}
