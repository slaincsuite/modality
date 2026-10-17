package cn.slainc.modules.upms.web.settings;

import cn.slainc.core.context.UserContext;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.dto.UserSettingsRequest;
import cn.slainc.modules.upms.entity.UserSettingsHistoryRecord;
import cn.slainc.modules.upms.entity.UserSettingsRecord;
import cn.slainc.modules.upms.service.settings.UserSettingsService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "用户设置", description = "新建用户默认值与全局账号安全策略")
@RestController
@RequestMapping("/upms/settings/user")
public class UserSettingsController {

    private final UserSettingsService settingsService;

    public UserSettingsController(UserSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_SETTINGS_QUERY)
    public UserSettingsRecord get() {
        return settingsService.current();
    }

    @PutMapping
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_SETTINGS_UPDATE)
    @AuditLog(value = "修改用户设置", operation = AuditOperation.UPDATE, category = AuditCategory.SECURITY,
            module = "upms")
    public UserSettingsRecord update(@RequestBody UserSettingsRequest request) {
        return settingsService.update(request, UserContext.username());
    }

    @GetMapping("/history")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_SETTINGS_QUERY)
    public List<UserSettingsHistoryRecord> history(@RequestParam(required = false) String settingKey,
                                                   @RequestParam(required = false) Integer limit) {
        return settingsService.history(settingKey, limit);
    }
}
