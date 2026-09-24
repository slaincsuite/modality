package cn.slainc.boot;

import cn.dev33.satoken.stp.StpInterface;
import cn.slainc.core.result.Result;
import cn.slainc.logger.audit.AuditCategory;
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.logger.audit.AuditOperation;
import cn.slainc.modules.upms.mapper.Batch8ScopeMapper;
import cn.slainc.core.enums.LoginType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class Batch8AcceptanceController {

    private final StpInterface stpInterface;

    private final Batch8ScopeMapper scopeMapper;

    public Batch8AcceptanceController(StpInterface stpInterface, Batch8ScopeMapper scopeMapper) {
        this.stpInterface = stpInterface;
        this.scopeMapper = scopeMapper;
    }

    @GetMapping("/upms/test/permissions")
    public Result<List<String>> permissions(@RequestHeader(value = "X-Login-Type", required = false) String type,
                                            @RequestHeader(value = "satoken", required = false) String token) {
        LoginType loginType = LoginType.of(type) == null ? LoginType.TENANT_USER : LoginType.of(type);
        Object loginId = cn.slainc.security.StpKit.of(loginType).getLoginIdDefaultNull();
        return Result.ok(loginId == null ? List.of()
                : stpInterface.getPermissionList(loginId, loginType.value()));
    }

    @GetMapping("/upms/test/scope-rows")
    public Result<List<String>> scopeRows() {
        return Result.ok(scopeMapper.selectScoped().stream().map(row -> row.getTitle()).toList());
    }

    @AuditLog(value = "演示操作", operation = AuditOperation.QUERY, category = AuditCategory.DATA, module = "upms")
    @GetMapping("/upms/test/audit")
    public Result<String> audit() {
        return Result.ok("done");
    }
}
