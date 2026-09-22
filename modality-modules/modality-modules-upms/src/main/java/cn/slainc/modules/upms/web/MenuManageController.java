package cn.slainc.modules.upms.web;

import cn.slainc.core.context.UserContext;
import cn.slainc.modules.upms.dto.MenuRequest;
import cn.slainc.modules.upms.entity.MenuRecord;
import cn.slainc.modules.upms.service.MenuService;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/upms/menu")
public class MenuManageController {

    private final MenuService menuService;

    public MenuManageController(MenuService menuService) {
        this.menuService = menuService;
    }

    @PostMapping
    @RequirePermission(permissions = "upms:menu:create")
    public MenuRecord create(@RequestBody MenuRequest request) {
        return menuService.create(request.name(), request.parentId(), request.type(), request.path(),
                request.component(), request.icon(), request.permissionCode(), request.visible(), request.sort());
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = "upms:menu:update")
    public MenuRecord update(@PathVariable Long id, @RequestBody MenuRequest request) {
        return menuService.update(id, request.name(), request.parentId(), request.type(), request.path(),
                request.component(), request.icon(), request.permissionCode(), request.visible(), request.sort(),
                request.status());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = "upms:menu:delete")
    public void delete(@PathVariable Long id) {
        menuService.delete(id);
    }

    @GetMapping("/tree")
    @RequirePermission(permissions = "upms:menu:query")
    public List<MenuRecord> tree() {
        return menuService.tree();
    }

    @GetMapping("/my-menus")
    @cn.slainc.security.annotation.RequireLogin
    public List<MenuRecord> myMenus() {
        return menuService.userMenuTree(new HashSet<>(UserContext.permissions()));
    }
}
