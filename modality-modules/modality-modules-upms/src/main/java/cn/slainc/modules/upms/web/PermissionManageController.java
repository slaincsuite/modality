package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.dto.PermissionRequest;
import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.excel.PermissionExcelService;
import cn.slainc.modules.upms.service.PermissionService;
import cn.slainc.security.annotation.RequirePermission;
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
    @RequirePermission(permissions = "upms:permission:create")
    public PermissionRecord create(@RequestBody PermissionRequest request) {
        return permissionService.create(request.code(), request.name(), request.type(), request.parentId(),
                request.sort(), request.remark());
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = "upms:permission:update")
    public PermissionRecord update(@PathVariable Long id, @RequestBody PermissionRequest request) {
        return permissionService.update(id, request.name(), request.type(), request.parentId(), request.sort(),
                request.status(), request.remark());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = "upms:permission:delete")
    public void delete(@PathVariable Long id) {
        permissionService.delete(id);
    }

    @GetMapping("/search")
    @RequirePermission(permissions = "upms:permission:query")
    public List<PermissionRecord> search(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String type) {
        return permissionService.search(keyword, type);
    }

    @GetMapping("/tree")
    @RequirePermission(permissions = "upms:permission:query")
    public List<PermissionRecord> tree() {
        return permissionService.tree();
    }

    @GetMapping("/export")
    @RequirePermission(permissions = "upms:permission:export")
    public void export(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=permissions.xlsx");
        permissionExcelService.export(response.getOutputStream());
    }

    @PostMapping("/import")
    @RequirePermission(permissions = "upms:permission:import")
    public Map<String, Object> importPermissions(@RequestParam("file") MultipartFile file) throws IOException {
        return permissionExcelService.importPermissions(file.getInputStream());
    }
}
