package cn.slainc.modules.open.web.portal;

import cn.slainc.modules.open.autoconfigure.OpenModuleProperties;
import cn.slainc.modules.open.dto.AppCreateRequest;
import cn.slainc.modules.open.dto.AppUpdateRequest;
import cn.slainc.modules.open.service.portal.OpenPortalService;
import cn.slainc.openapi.domain.App;
import cn.slainc.openapi.domain.AppCredential;
import cn.slainc.security.annotation.RequireLogin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;

// 开发者门户：本人凭据端点（登录即可用，无管理权限码）
@Tag(name = "开发者门户", description = "应用接入自助查询")
@RestController
@RequestMapping("/open/portal")
public class OpenPortalController {

    private final OpenPortalService portalService;

    private final long rotateGraceMillis;

    public OpenPortalController(OpenPortalService portalService, OpenModuleProperties properties) {
        this.portalService = portalService;
        this.rotateGraceMillis = properties.getCredential().getRotateGraceHours() * 3600_000L;
    }

    @GetMapping("/apps")
    @RequireLogin
    public List<App> myApps() {
        return portalService.myApps();
    }

    @PostMapping("/apps")
    @RequireLogin
    public Object create(@RequestBody AppCreateRequest request) {
        return portalService.create(request);
    }

    @PutMapping("/apps/{appId}")
    @RequireLogin
    public Object update(@PathVariable String appId, @RequestBody AppUpdateRequest request) {
        return portalService.update(appId, request);
    }

    @PostMapping("/apps/{appId}/submit")
    @RequireLogin
    public Object submit(@PathVariable String appId) {
        return portalService.submit(appId);
    }

    @GetMapping("/apps/{appId}/logs")
    @RequireLogin
    public Object appLogs(@PathVariable String appId) {
        return portalService.appLogs(appId);
    }

    @GetMapping("/scopes")
    @RequireLogin
    public Object scopeCatalog() {
        return portalService.scopeCatalog();
    }

    @GetMapping("/apps/{appId}/credential")
    @RequireLogin
    public AppCredential viewCredential(@PathVariable String appId) {
        return portalService.viewCredential(appId);
    }

    @PostMapping("/apps/{appId}/credential")
    @RequireLogin
    public AppCredential issueCredential(@PathVariable String appId) {
        return portalService.issueCredential(appId, rotateGraceMillis);
    }

    @PostMapping("/apps/{appId}/credential/rotate")
    @RequireLogin
    public AppCredential rotateCredential(@PathVariable String appId) {
        return portalService.rotateCredential(appId, rotateGraceMillis);
    }

    @GetMapping("/consents")
    @RequireLogin
    public Object myConsents() {
        return portalService.myConsents();
    }
}
