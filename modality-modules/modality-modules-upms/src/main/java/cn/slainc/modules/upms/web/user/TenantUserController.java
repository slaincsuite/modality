package cn.slainc.modules.upms.web.user;

import cn.slainc.core.result.PageResult;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.excel.UserExcelService;
import cn.slainc.modules.upms.service.user.UserService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.web.http.DownloadResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "租户成员", description = "按产品路径查询指定租户的用户")
@RestController
@RequestMapping("/tenant")
public class TenantUserController {

    private final UserService userService;

    private final UserExcelService userExcelService;

    public TenantUserController(UserService userService, UserExcelService userExcelService) {
        this.userService = userService;
        this.userExcelService = userExcelService;
    }

    @GetMapping("/{id}/users")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_QUERY)
    public PageResult<UserRecord> users(@PathVariable Long id, @RequestParam(required = false) String keyword,
                                        @RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "10") long size) {
        return userService.searchForTenant(id, keyword, current, size);
    }

    @GetMapping("/{id}/users/export")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_USER_EXPORT)
    @AuditLog(value = "导出指定租户用户", operation = AuditOperation.EXPORT, category = AuditCategory.DATA,
            module = "upms")
    public void export(@PathVariable Long id, @RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "100000") long size,
                       HttpServletResponse response) {
        List<UserRecord> users = userService.searchForTenant(id, keyword, current, size).records();
        userExcelService.export(DownloadResponses.attachmentStream(response, "tenant-users.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL), users);
    }
}
