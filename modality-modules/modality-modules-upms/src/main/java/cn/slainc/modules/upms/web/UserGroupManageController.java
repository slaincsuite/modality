package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.UpmsKeys;

import cn.slainc.modules.upms.dto.GroupMembersRequest;
import cn.slainc.modules.upms.dto.GroupRequest;
import cn.slainc.modules.upms.entity.UserGroupRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.UserGroupService;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditOperation;

@Tag(name = "用户组管理", description = "用户组成员与组策略管理")
@RestController
@RequestMapping("/upms/group")
public class UserGroupManageController {

    private final UserGroupService groupService;

    public UserGroupManageController(UserGroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_GROUP_CREATE)
    @AuditLog(value = "创建用户组", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "upms")
    public UserGroupRecord create(@RequestBody GroupRequest request) {
        return groupService.create(request.name(), request.remark());
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_GROUP_UPDATE)
    @AuditLog(value = "修改用户组", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "upms")
    public UserGroupRecord update(@PathVariable Long id, @RequestBody GroupRequest request) {
        return groupService.update(id, request.name(), request.remark());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_GROUP_DELETE)
    @AuditLog(value = "删除用户组", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "upms")
    public void delete(@PathVariable Long id) {
        groupService.delete(id);
    }

    @GetMapping("/list")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_GROUP_QUERY)
    public List<UserGroupRecord> list() {
        return groupService.list();
    }

    @PutMapping("/{id}/members")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_GROUP_ASSIGN)
    @AuditLog(value = "绑定用户组成员", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "upms")
    public void assignMembers(@PathVariable Long id, @RequestBody GroupMembersRequest request) {
        groupService.assignMembers(id, request.userIds());
    }

    @GetMapping("/{id}/members")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_GROUP_QUERY)
    public List<UserRecord> members(@PathVariable Long id) {
        return groupService.membersOf(id);
    }
}
