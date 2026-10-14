package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.UpmsKeys;

import cn.slainc.modules.upms.dto.GrantRequest;
import cn.slainc.modules.upms.entity.AccessControlRecord;
import cn.slainc.modules.upms.service.AccessControlService;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditOperation;

@Tag(name = "访问控制", description = "当前登录用户的权限与资料查询")
@RestController
@RequestMapping("/upms/access")
public class AccessControlController {

    private final AccessControlService accessControlService;

    public AccessControlController(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    @PostMapping("/grant")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ACCESS_GRANT)
    @AuditLog(value = "授予系统访问权限", operation = AuditOperation.GRANT, category = AuditCategory.PERMISSION,
            module = "upms")
    public Map<String, Object> grant(@RequestBody GrantRequest request) {
        List<AccessControlRecord> granted = accessControlService.grant(request.systemId(), request.userIds(), null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("granted", granted.size());
        return result;
    }

    @DeleteMapping("/revoke")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ACCESS_REVOKE)
    @AuditLog(value = "解除系统访问权限", operation = AuditOperation.REVOKE, category = AuditCategory.PERMISSION,
            module = "upms")
    public void revoke(@RequestParam Long systemId, @RequestParam Long userId) {
        accessControlService.revoke(systemId, userId);
    }

    @DeleteMapping("/system/{systemId}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ACCESS_REVOKE)
    @AuditLog(value = "批量解除系统访问权限", operation = AuditOperation.DELETE, category = AuditCategory.SYSTEM,
            module = "upms")
    public Map<String, Object> revokeBySystem(@PathVariable Long systemId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revoked", accessControlService.revokeBySystem(systemId));
        return result;
    }

    @GetMapping("/query")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_ACCESS_QUERY)
    public List<AccessControlRecord> query(@RequestParam(required = false) Long systemId,
                                           @RequestParam(required = false) Long userId) {
        return accessControlService.query(systemId, userId);
    }
}
