package cn.slainc.modules.open.web;

import cn.slainc.core.context.UserContext;
import cn.slainc.logger.audit.AuditCategory;
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.logger.audit.AuditOperation;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.dto.ReviewRequest;
import cn.slainc.modules.open.service.OpenAppAdminService;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/open/admin/apps")
public class OpenAppAdminController {

    private final OpenAppAdminService adminService;

    public OpenAppAdminController(OpenAppAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/page")
    @RequirePermission(permissions = OpenKeys.PERMISSION_APP_QUERY)
    public Object page(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String appType,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String ownerId,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return adminService.page(keyword, category, appType, status, ownerId, current, size);
    }

    @GetMapping("/{appId}")
    @RequirePermission(permissions = OpenKeys.PERMISSION_APP_QUERY)
    public Map<String, Object> detail(@PathVariable String appId) {
        return adminService.detail(appId);
    }

    @PostMapping("/{appId}/approve")
    @RequirePermission(permissions = OpenKeys.PERMISSION_APP_REVIEW)
    @AuditLog(value = "审核通过开放平台应用", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "open")
    public Object approve(@PathVariable String appId, @RequestBody(required = false) ReviewRequest request) {
        return adminService.approve(appId, request == null ? null : request.reason(), UserContext.username());
    }

    @PostMapping("/{appId}/reject")
    @RequirePermission(permissions = OpenKeys.PERMISSION_APP_REVIEW)
    @AuditLog(value = "驳回开放平台应用", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "open")
    public Object reject(@PathVariable String appId, @RequestBody(required = false) ReviewRequest request) {
        return adminService.reject(appId, request == null ? null : request.reason(), UserContext.username());
    }

    @PostMapping("/{appId}/offline")
    @RequirePermission(permissions = OpenKeys.PERMISSION_APP_OFFLINE)
    @AuditLog(value = "下线开放平台应用", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "open")
    public Object offline(@PathVariable String appId, @RequestBody(required = false) ReviewRequest request) {
        return adminService.offline(appId, request == null ? null : request.reason(), UserContext.username());
    }

    @PostMapping("/{appId}/republish")
    @RequirePermission(permissions = OpenKeys.PERMISSION_APP_REVIEW)
    @AuditLog(value = "重新上架开放平台应用", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "open")
    public Object republish(@PathVariable String appId) {
        return adminService.republish(appId, UserContext.username());
    }

    @DeleteMapping("/{appId}")
    @RequirePermission(permissions = OpenKeys.PERMISSION_APP_DELETE)
    @AuditLog(value = "删除开放平台应用", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "open")
    public Object delete(@PathVariable String appId) {
        return adminService.delete(appId, UserContext.username());
    }

    @GetMapping("/{appId}/logs")
    @RequirePermission(permissions = OpenKeys.PERMISSION_APP_QUERY)
    public Object logs(@PathVariable String appId, @RequestParam(defaultValue = "50") int limit) {
        return adminService.logs(appId, limit);
    }
}
