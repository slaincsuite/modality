package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.UpmsKeys;

import cn.slainc.modules.upms.dto.PermissionRequest;
import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.excel.PermissionExcelService;
import cn.slainc.modules.upms.service.PermissionService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.web.http.DownloadResponses;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditOperation;

@Tag(name = "权限管理", description = "权限码与权限点维护")
@RestController
@RequestMapping("/upms/permission")
public class PermissionManageController {

    private final PermissionService permissionService;

    private final PermissionExcelService permissionExcelService;

    public PermissionManageController(PermissionService permissionService,
                                      PermissionExcelService permissionExcelService) {
        this.permissionService = permissionService;
        this.permissionExcelService = permissionExcelService;
    }

    @PostMapping
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_PERMISSION_CREATE)
    @AuditLog(value = "创建权限", operation = AuditOperation.CREATE, category = AuditCategory.PERMISSION,
            module = "upms")
    public PermissionRecord create(@RequestBody PermissionRequest request) {
        return permissionService.create(request.code(), request.name(), request.type(), request.parentId(),
                request.sort(), request.remark());
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_PERMISSION_UPDATE)
    @AuditLog(value = "修改权限", operation = AuditOperation.UPDATE, category = AuditCategory.PERMISSION,
            module = "upms")
    public PermissionRecord update(@PathVariable Long id, @RequestBody PermissionRequest request) {
        return permissionService.update(id, request.name(), request.type(), request.parentId(), request.sort(),
                request.status(), request.remark());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_PERMISSION_DELETE)
    @AuditLog(value = "删除权限", operation = AuditOperation.DELETE, category = AuditCategory.PERMISSION,
            module = "upms")
    public void delete(@PathVariable Long id) {
        permissionService.delete(id);
    }

    @GetMapping("/search")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_PERMISSION_QUERY)
    public List<PermissionRecord> search(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String type) {
        return permissionService.search(keyword, type);
    }

    @GetMapping("/tree")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_PERMISSION_QUERY)
    public List<PermissionRecord> tree() {
        return permissionService.tree();
    }

    @GetMapping("/export")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_PERMISSION_EXPORT)
    public void export(HttpServletResponse response) {
        permissionExcelService.export(DownloadResponses.attachmentStream(response, "permissions.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL));
    }

    @PostMapping("/import")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_PERMISSION_IMPORT)
    @AuditLog(value = "导入权限", operation = AuditOperation.IMPORT, category = AuditCategory.PERMISSION,
            module = "upms")
    public Map<String, Object> importPermissions(@RequestParam("file") MultipartFile file) throws IOException {
        return permissionExcelService.importPermissions(file.getInputStream());
    }
}
