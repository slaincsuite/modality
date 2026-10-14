package cn.slainc.openapi.support;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.openapi.domain.App;
import cn.slainc.openapi.domain.AppCredential;
import cn.slainc.openapi.spi.AppStore;
import cn.slainc.openapi.spi.CredentialProvisioner;
import cn.slainc.openapi.spi.CredentialService;
import cn.slainc.openapi.spi.CredentialStore;

import java.util.Comparator;
import java.util.List;

public class DefaultCredentialService implements CredentialService {

    private final AppStore appStore;

    private final CredentialStore credentialStore;

    private final List<CredentialProvisioner> provisioners;

    public DefaultCredentialService(AppStore appStore, CredentialStore credentialStore,
                                    List<CredentialProvisioner> provisioners) {
        this.appStore = appStore;
        this.credentialStore = credentialStore;
        this.provisioners = provisioners == null ? List.of() : provisioners.stream()
                .sorted(Comparator.comparingInt(CredentialProvisioner::priority).reversed()).toList();
    }

    @Override
    public AppCredential issue(String appId) {
        App app = requireApp(appId);
        if (credentialStore.find(appId).isPresent()) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "应用 " + appId + " 已签发过凭证，请轮换而非重复签发");
        }
        AppCredential credential = pick(app).issue(app);
        credential.setAppId(appId);
        credential.setIssuedAt(System.currentTimeMillis());
        return credentialStore.save(credential);
    }

    @Override
    public AppCredential rotate(String appId, long graceMillis) {
        App app = requireApp(appId);
        AppCredential existing = credentialStore.find(appId)
                .orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND, "应用 " + appId + " 尚未签发凭证"));
        CredentialProvisioner provisioner = pick(app);
        AppCredential rotated = provisioner.rotate(existing, app, graceMillis);
        rotated.setAppId(appId);
        rotated.setIssuedAt(existing.getIssuedAt());
        rotated.setRotatedAt(System.currentTimeMillis());
        return credentialStore.save(rotated);
    }

    @Override
    public AppCredential enable(String appId) {
        return changeStatus(appId, true);
    }

    @Override
    public AppCredential disable(String appId) {
        return changeStatus(appId, false);
    }

    @Override
    public AppCredential find(String appId) {
        return credentialStore.find(appId)
                .orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND, "应用 " + appId + " 尚未签发凭证"));
    }

    private AppCredential changeStatus(String appId, boolean enabled) {
        App app = requireApp(appId);
        AppCredential existing = credentialStore.find(appId)
                .orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND, "应用 " + appId + " 尚未签发凭证"));
        pick(app).changeStatus(existing.getClientId(), enabled);
        existing.setStatus(enabled ? AppCredential.Status.ENABLED : AppCredential.Status.DISABLED);
        return credentialStore.save(existing);
    }

    private CredentialProvisioner pick(App app) {
        return provisioners.stream()
                .filter(provisioner -> provisioner.supports(app))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.DATA_STATE_INVALID,
                        "没有可用的凭证签发器支持应用 " + app.getAppId()));
    }

    private App requireApp(String appId) {
        return appStore.find(appId)
                .orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND, "应用 " + appId + " 不存在"));
    }
}
