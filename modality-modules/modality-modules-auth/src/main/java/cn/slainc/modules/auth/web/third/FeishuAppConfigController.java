package cn.slainc.modules.auth.web.third;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.dto.FeishuConfigRequest;
import cn.slainc.modules.auth.dto.FeishuStatusRequest;
import cn.slainc.modules.auth.service.third.FeishuAppConfigService;
import cn.slainc.modules.auth.service.third.FeishuContactSyncService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "飞书应用配置", description = "飞书联合登录配置、通讯录同步与同步记录")
@RestController
@RequestMapping("/auth/third/feishu")
public class FeishuAppConfigController {

    private final FeishuAppConfigService configService;

    private final FeishuContactSyncService syncService;

    public FeishuAppConfigController(FeishuAppConfigService configService, FeishuContactSyncService syncService) {
        this.configService = configService;
        this.syncService = syncService;
    }

    @GetMapping("/config")
    @RequirePermission(permissions = AuthKeys.PERMISSION_THIRD_CONFIG_QUERY)
    public Result<FeishuAppConfigService.AppConfigView> config() {
        return Result.ok(configService.get());
    }

    @PutMapping("/config")
    @RequirePermission(permissions = AuthKeys.PERMISSION_THIRD_CONFIG_UPDATE)
    @AuditLog(value = "修改飞书应用配置", operation = AuditOperation.CONFIG, category = AuditCategory.SECURITY,
            module = "auth", recordParams = false)
    public Result<FeishuAppConfigService.AppConfigView> updateConfig(@RequestBody FeishuConfigRequest request) {
        return Result.ok(configService.update(request));
    }

    @PostMapping("/secret/reset")
    @RequirePermission(permissions = AuthKeys.PERMISSION_THIRD_CONFIG_UPDATE)
    @AuditLog(value = "重置飞书应用 Secret", operation = AuditOperation.CONFIG, category = AuditCategory.SECURITY,
            module = "auth")
    public Result<FeishuAppConfigService.AppConfigView> resetSecret() {
        return Result.ok(configService.resetSecret());
    }

    @PutMapping("/status")
    @RequirePermission(permissions = AuthKeys.PERMISSION_THIRD_CONFIG_UPDATE)
    @AuditLog(value = "修改飞书联合登录状态", operation = AuditOperation.CONFIG, category = AuditCategory.SECURITY,
            module = "auth")
    public Result<FeishuAppConfigService.AppConfigView> status(@RequestBody FeishuStatusRequest request) {
        return Result.ok(configService.changeStatus(request.enabled()));
    }

    @PostMapping("/sync")
    @RequirePermission(permissions = AuthKeys.PERMISSION_THIRD_SYNC_EXECUTE)
    @AuditLog(value = "同步飞书通讯录", operation = AuditOperation.OTHER, category = AuditCategory.SECURITY,
            module = "auth")
    public Result<FeishuContactSyncService.SyncRecordView> sync() {
        return Result.ok(syncService.sync(UserContext.username()));
    }

    @GetMapping("/sync-records")
    @RequirePermission(permissions = AuthKeys.PERMISSION_THIRD_SYNC_QUERY)
    public Result<PageResult<FeishuContactSyncService.SyncRecordView>> syncRecords(
            @RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status) {
        return Result.ok(syncService.page(status, current, size));
    }

    @GetMapping(value = "/sync-records/export", produces = "text/csv;charset=UTF-8")
    @RequirePermission(permissions = AuthKeys.PERMISSION_THIRD_SYNC_EXPORT)
    @AuditLog(value = "导出飞书同步记录", operation = AuditOperation.EXPORT, category = AuditCategory.SECURITY,
            module = "auth")
    public ResponseEntity<String> export(@RequestParam(required = false) String status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("feishu-sync-records.csv")
                .build());
        return ResponseEntity.ok().headers(headers).body(syncService.exportCsv(status));
    }
}
