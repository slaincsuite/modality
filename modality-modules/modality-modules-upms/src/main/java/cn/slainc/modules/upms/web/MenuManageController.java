package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.UpmsKeys;

import cn.slainc.core.context.UserContext;
import cn.slainc.modules.upms.dto.MenuRequest;
import cn.slainc.modules.upms.entity.MenuRecord;
import cn.slainc.modules.upms.service.MenuService;
import cn.slainc.modules.upms.service.RecentVisitService;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.logger.audit.AuditCategory;
import cn.slainc.logger.audit.AuditOperation;

@Tag(name = "菜单管理", description = "菜单树与页面资源维护")
@RestController
@RequestMapping("/upms/menu")
public class MenuManageController {

    private final MenuService menuService;

    private final RecentVisitService recentVisitService;

    public MenuManageController(MenuService menuService, RecentVisitService recentVisitService) {
        this.menuService = menuService;
        this.recentVisitService = recentVisitService;
    }

    @GetMapping("/hot")
    public List<MenuRecord> hot(@RequestParam(required = false) Integer limit) {
        return recentVisitService.hot(limit);
    }

    @PostMapping
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_MENU_CREATE)
    @AuditLog(value = "创建菜单", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "upms")
    public MenuRecord create(@RequestBody MenuRequest request) {
        return menuService.create(request.name(), request.parentId(), request.type(), request.path(),
                request.component(), request.icon(), request.permissionCode(), request.visible(), request.sort());
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_MENU_UPDATE)
    @AuditLog(value = "修改菜单", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "upms")
    public MenuRecord update(@PathVariable Long id, @RequestBody MenuRequest request) {
        return menuService.update(id, request.name(), request.parentId(), request.type(), request.path(),
                request.component(), request.icon(), request.permissionCode(), request.visible(), request.sort(),
                request.status());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_MENU_DELETE)
    @AuditLog(value = "删除菜单", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "upms")
    public void delete(@PathVariable Long id) {
        menuService.delete(id);
    }

    @GetMapping("/tree")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_MENU_QUERY)
    public List<MenuRecord> tree() {
        return menuService.tree();
    }

    @GetMapping("/my-menus")
    @cn.slainc.security.annotation.RequireLogin
    public List<MenuRecord> myMenus() {
        return menuService.userMenuTree(new HashSet<>(UserContext.permissions()));
    }
}
