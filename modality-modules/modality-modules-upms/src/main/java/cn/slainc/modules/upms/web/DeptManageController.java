package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.dto.DeptRequest;
import cn.slainc.modules.upms.entity.DeptRecord;
import cn.slainc.modules.upms.service.DeptService;
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

import java.util.List;

@RestController
@RequestMapping("/upms/dept")
public class DeptManageController {

    private final DeptService deptService;

    public DeptManageController(DeptService deptService) {
        this.deptService = deptService;
    }

    @PostMapping
    @RequirePermission(permissions = "upms:dept:create")
    public DeptRecord create(@RequestBody DeptRequest request) {
        return deptService.create(request.name(), request.parentId(), request.leaderUserId(), request.sort());
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = "upms:dept:update")
    public DeptRecord update(@PathVariable Long id, @RequestBody DeptRequest request) {
        return deptService.update(id, request.name(), request.parentId(), request.leaderUserId(), request.sort(),
                request.status());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = "upms:dept:delete")
    public void delete(@PathVariable Long id) {
        deptService.delete(id);
    }

    @GetMapping("/tree")
    @RequirePermission(permissions = "upms:dept:query")
    public List<DeptRecord> tree() {
        return deptService.tree();
    }

    @GetMapping("/{id}/users")
    @RequirePermission(permissions = "upms:dept:query")
    public Object users(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean includeChildren,
                        @RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "10") long size) {
        return deptService.usersOf(id, includeChildren, current, size);
    }
}
