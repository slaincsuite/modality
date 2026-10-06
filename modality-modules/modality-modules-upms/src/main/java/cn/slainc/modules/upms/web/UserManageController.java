package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.UpmsKeys;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.dto.AvatarRequest;
import cn.slainc.modules.upms.dto.ChangePasswordRequest;
import cn.slainc.modules.upms.dto.ResetPasswordRequest;
import cn.slainc.modules.upms.dto.RoleAssignRequest;
import cn.slainc.modules.upms.dto.UserCreateRequest;
import cn.slainc.modules.upms.dto.UserUpdateRequest;
import cn.slainc.modules.upms.entity.UserLoginRecordRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.excel.UserExcelService;
import cn.slainc.modules.upms.service.UserService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/upms/user")
public class UserManageController {

    private final UserService userService;

    private final cn.slainc.modules.upms.service.UserPasswordService passwordService;

    private final UserExcelService userExcelService;

    public UserManageController(UserService userService,
                                cn.slainc.modules.upms.service.UserPasswordService passwordService,
                                UserExcelService userExcelService) {
        this.userService = userService;
        this.passwordService = passwordService;
        this.userExcelService = userExcelService;
    }

    @PostMapping
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_CREATE)
    public UserRecord create(@RequestBody UserCreateRequest request) {
        LoginType type = request.loginType() == null || request.loginType().isBlank()
                ? LoginType.TENANT_USER : LoginType.of(request.loginType());
        if (type == null) {
            throw new IllegalArgumentException("非法的账号体系: " + request.loginType());
        }
        return userService.create(request.username(), request.nickname(), request.password(), request.email(),
                request.phone(), request.deptId(), type, request.tenantId(), UserContext.username());
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_UPDATE)
    public UserRecord update(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        return userService.update(id, request.nickname(), request.email(), request.phone(), request.deptId(),
                request.avatarUrl(), request.tags(), request.extJson(), request.allowedIps(),
                request.allowedTimeRange(), request.allowedDevices());
    }

    @GetMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_QUERY)
    public UserRecord get(@PathVariable Long id) {
        return userService.get(id);
    }

    @GetMapping("/page")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_QUERY)
    public Object page(@RequestParam(required = false) String username, @RequestParam(required = false) String nickname,
                       @RequestParam(required = false) String phone, @RequestParam(required = false) String email,
                       @RequestParam(required = false) String status, @RequestParam(required = false) Long deptId,
                       @RequestParam(required = false) Long roleId, @RequestParam(required = false) Long groupId,
                       @RequestParam(required = false) String loginType,
                       @RequestParam(required = false) Long createdFrom,
                       @RequestParam(required = false) Long createdTo,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return userService.search(username, nickname, phone, email, status, deptId, roleId, groupId, loginType,
                createdFrom, createdTo, current, size);
    }

    @GetMapping("/{id}/detail")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_QUERY)
    public Map<String, Object> detail(@PathVariable Long id) {
        UserRecord user = userService.get(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("user", user);
        detail.put("roleIds", userService.roleIdsOf(id));
        detail.put("recentLogins", userService.recentLogins(id, 10));
        return detail;
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_DELETE)
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }

    @PostMapping("/batch-delete")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_DELETE)
    public Map<String, Object> batchDelete(@RequestBody List<Long> ids) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", userService.batchDelete(ids));
        return result;
    }

    @PostMapping("/batch-disable")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_DISABLE)
    public Map<String, Object> batchDisable(@RequestBody List<Long> ids) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("disabled", userService.batchDisable(ids));
        return result;
    }

    @PutMapping("/{id}/status")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_DISABLE)
    public void changeStatus(@PathVariable Long id, @RequestParam String status) {
        userService.changeStatus(id, status);
    }

    @PutMapping("/{id}/password/reset")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_RESET_PASSWORD)
    public void resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest request) {
        passwordService.resetByAdmin(id, request.newPassword());
    }

    @PutMapping("/password/change")
    @cn.slainc.security.annotation.RequireLogin
    public void changePassword(@RequestBody ChangePasswordRequest request) {
        LoginType type = UserContext.loginType() == null ? LoginType.TENANT_USER : UserContext.loginType();
        passwordService.changeBySelf(UserContext.userId(), type, request.oldPassword(), request.newPassword());
    }

    @PostMapping("/{id}/roles")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_ASSIGN_ROLE)
    public void assignRoles(@PathVariable Long id, @RequestBody RoleAssignRequest request) {
        userService.assignRoles(id, request.roleIds());
    }

    @PostMapping("/{id}/merge/{targetId}")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_MERGE)
    public void merge(@PathVariable Long id, @PathVariable Long targetId) {
        userService.merge(id, targetId);
    }

    @PutMapping("/{id}/avatar")
    @cn.slainc.security.annotation.RequireLogin
    public void setAvatar(@PathVariable Long id, @RequestBody AvatarRequest request) {
        userService.setAvatar(id, request.avatarUrl());
    }

    @GetMapping("/{id}/recent-logins")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_QUERY)
    public List<UserLoginRecordRecord> recentLogins(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "10") int limit) {
        return userService.recentLogins(id, limit);
    }

    @GetMapping("/export")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_EXPORT)
    public void export(@RequestParam(required = false) String username, @RequestParam(required = false) String status,
                       @RequestParam(required = false) Long deptId, @RequestParam(required = false) Long roleId,
                       @RequestParam(required = false) Long groupId, HttpServletResponse response) {
        List<UserRecord> users = userService.search(username, null, null, null, status, deptId, roleId, groupId,
                null, null, null, 1, 100000).records();
        userExcelService.export(DownloadResponses.attachmentStream(response, "users.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL), users);
    }

    @PostMapping("/import")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_IMPORT)
    public Map<String, Object> importUsers(@RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) Long tenantId) throws IOException {
        return userExcelService.importUsers(file.getInputStream(), tenantId, UserContext.username());
    }

    @GetMapping("/import-template")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_IMPORT)
    public void importTemplate(HttpServletResponse response) {
        userExcelService.template(DownloadResponses.attachmentStream(response, "users-template.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL));
    }
}
