package cn.slainc.modules.open.service.portal;

import cn.slainc.modules.open.service.app.OpenAppAdminService;
import cn.slainc.modules.open.service.app.RedirectUriValidator;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.config.OpenConfigReader;
import cn.slainc.modules.open.dto.AppCreateRequest;
import cn.slainc.modules.open.dto.AppUpdateRequest;
import cn.slainc.openapi.domain.App;
import cn.slainc.openapi.domain.AppStatus;
import cn.slainc.openapi.domain.AppType;
import cn.slainc.openapi.domain.AppCredential;
import cn.slainc.openapi.domain.ConsentRecord;
import cn.slainc.openapi.spi.AppService;
import cn.slainc.openapi.spi.ConsentService;
import cn.slainc.openapi.spi.CredentialService;
import cn.slainc.openapi.spi.ScopeService;

import java.util.List;

// 开发者门户：本人应用自助创建/提交/凭证获取，Scope 目录与本人授权记录（第三方应用自助接入的落点）
public class OpenPortalService {

    private final AppService appService;

    private final ScopeService scopeService;

    private final CredentialService credentialService;

    private final ConsentService consentService;

    private final OpenConfigReader configReader;

    private final OpenAppAdminService adminService;

    public OpenPortalService(AppService appService, ScopeService scopeService, CredentialService credentialService,
                             ConsentService consentService, OpenConfigReader configReader,
                             OpenAppAdminService adminService) {
        this.appService = appService;
        this.scopeService = scopeService;
        this.credentialService = credentialService;
        this.consentService = consentService;
        this.configReader = configReader;
        this.adminService = adminService;
    }

    public List<App> myApps() {
        return appService.listByOwner(currentOwner());
    }

    public App create(AppCreateRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "应用名称不能为空");
        }
        AppType type = parseType(request.appType());
        RedirectUriValidator.validate(request.redirectUris());
        validateCategory(request.category());
        App app = App.create(request.name().trim(), type, currentOwner());
        app.setDescription(request.description());
        app.setLogo(request.logo());
        app.setCategory(request.category());
        app.setContact(request.contact());
        app.setTags(request.tags());
        app.setRedirectUris(request.redirectUris());
        app.setScopes(request.scopes());
        App saved = appService.create(app);
        adminService.log(saved.getAppId(), "CREATE", currentOwner(), null, null);
        return saved;
    }

    public App update(String appId, AppUpdateRequest request) {
        App existing = ownApp(appId);
        RedirectUriValidator.validate(request.redirectUris());
        validateCategory(request.category());
        if (request.name() != null && !request.name().isBlank()) {
            existing.setName(request.name().trim());
        }
        existing.setDescription(request.description());
        existing.setLogo(request.logo());
        existing.setCategory(request.category());
        existing.setContact(request.contact());
        existing.setTags(request.tags());
        existing.setRedirectUris(request.redirectUris());
        existing.setScopes(request.scopes());
        App saved = appService.update(existing);
        adminService.log(appId, "UPDATE", currentOwner(), null, null);
        return saved;
    }

    public App submit(String appId) {
        App existing = ownApp(appId);
        if (existing.getStatus() != AppStatus.DRAFT && existing.getStatus() != AppStatus.OFFLINE) {
            throw new BizException(ResultCode.DATA_STATE_INVALID,
                    "应用当前状态「" + existing.getStatus().label() + "」不能提交审核");
        }
        App submitted = appService.submit(appId);
        adminService.log(appId, "SUBMIT", currentOwner(), null, null);
        if (configReader.autoApprove()) {
            submitted = appService.approve(appId);
            adminService.log(appId, "APPROVE", "system", "热配置自动通过", null);
        }
        return submitted;
    }

    public List<Object> scopeCatalog() {
        return List.copyOf(scopeService.list());
    }

    public AppCredential viewCredential(String appId) {
        ownApp(appId);
        AppCredential credential = credentialService.find(appId);
        if (credential == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND,
                    "应用 " + appId + " 尚未签发凭证，请先调用获取凭证接口");
        }
        return credential;
    }

    public AppCredential issueCredential(String appId, long rotateGraceMillis) {
        App app = ownApp(appId);
        if (app.getStatus() != AppStatus.ONLINE) {
            throw new BizException(ResultCode.DATA_STATE_INVALID,
                    "应用当前状态「" + app.getStatus().label() + "」，仅已上线应用可签发凭证");
        }
        if (credentialIssued(appId)) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "凭证已签发，如需更换请使用重置接口");
        }
        AppCredential credential = credentialService.issue(appId);
        adminService.log(appId, "CREDENTIAL_ISSUE", currentOwner(), null, credential.getType().name());
        return credential;
    }

    public AppCredential rotateCredential(String appId, long rotateGraceMillis) {
        ownApp(appId);
        if (!credentialIssued(appId)) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "应用 " + appId + " 尚未签发凭证，无法轮换");
        }
        AppCredential credential = credentialService.rotate(appId, rotateGraceMillis);
        adminService.log(appId, "CREDENTIAL_ROTATE", currentOwner(), null, credential.getType().name());
        return credential;
    }

    // 库的 CredentialService.find 在未签发时抛业务异常，这里收敛成布尔判定
    private boolean credentialIssued(String appId) {
        try {
            credentialService.find(appId);
            return true;
        } catch (BizException e) {
            return false;
        }
    }

    public List<ConsentRecord> myConsents() {
        return consentService.listByUser(currentOwner());
    }

    public List<Object> appLogs(String appId) {
        ownApp(appId);
        return adminService.logs(appId, 50);
    }

    private App ownApp(String appId) {
        App app = appService.get(appId);
        if (!currentOwner().equals(app.getOwnerId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只能操作本人名下的应用 " + appId);
        }
        return app;
    }

    private String currentOwner() {
        Long userId = UserContext.userId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "开发者身份未识别，请先登录");
        }
        return String.valueOf(userId);
    }

    private void validateCategory(String category) {
        List<String> categories = configReader.categories();
        if (category == null || category.isBlank() || categories.isEmpty()) {
            return;
        }
        if (categories.stream().noneMatch(category.trim()::equals)) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR,
                    "应用分类不在允许范围内: " + category + "，可选: " + String.join("、", categories));
        }
    }

    private AppType parseType(String appType) {
        if (appType == null || appType.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "应用类型不能为空");
        }
        try {
            return AppType.valueOf(appType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR,
                    "应用类型只允许 WEB、MOBILE、SERVER、DESKTOP，当前为 " + appType);
        }
    }
}
