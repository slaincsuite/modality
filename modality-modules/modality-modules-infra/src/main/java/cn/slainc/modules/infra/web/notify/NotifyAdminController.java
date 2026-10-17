package cn.slainc.modules.infra.web.notify;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.service.notify.NotifyService;
import cn.slainc.security.annotation.RequirePermission;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "通知渠道管理", description = "渠道配置与模板维护")
@RestController
@RequestMapping("/infra/notify")
public class NotifyAdminController {

    private final NotifyService notifyService;

    public NotifyAdminController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_QUERY)
    @GetMapping("/templates/page")
    public Result<PageResult<NotifyService.TemplateView>> templatePage(
            @RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(notifyService.templatePage(current, size, keyword));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_CREATE)
    @AuditLog(value = "创建通知模板", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "infra")
    @PostMapping("/templates")
    public Result<Void> createTemplate(@RequestBody Map<String, Object> body) {
        notifyService.createTemplate(text(body.get("code")), text(body.get("name")), text(body.get("titleTemplate")),
                text(body.get("contentTemplate")), text(body.get("channel")));
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_UPDATE)
    @AuditLog(value = "修改通知模板", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra")
    @PutMapping("/templates/{id}")
    public Result<Void> updateTemplate(@PathVariable long id, @RequestBody Map<String, Object> body) {
        notifyService.updateTemplate(id, text(body.get("name")), text(body.get("titleTemplate")),
                text(body.get("contentTemplate")),
                body.containsKey("enabled") ? Boolean.parseBoolean(text(body.get("enabled"))) : null);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_DELETE)
    @AuditLog(value = "删除通知模板", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "infra")
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable long id) {
        notifyService.deleteTemplate(id);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_SEND)
    @AuditLog(value = "发送站内信", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "infra")
    @PostMapping("/in-app")
    public Result<Long> sendInApp(@RequestBody Map<String, Object> body) {
        Object rawReceivers = body.get("receivers");
        cn.slainc.core.lang.BizAssert.isTrue(rawReceivers instanceof List<?>, "收件人列表不能为空");
        List<Long> receivers = ((List<?>) rawReceivers).stream()
                .map(receiver -> Long.parseLong(String.valueOf(receiver)))
                .toList();
        return Result.ok(notifyService.sendInApp(text(body.get("templateCode")), params(body.get("params")),
                receivers, text(body.get("loginType"))));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_QUERY)
    @GetMapping("/webhooks/page")
    public Result<PageResult<NotifyService.WebhookView>> webhookPage(
            @RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(notifyService.webhookPage(current, size, keyword));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_CREATE)
    @AuditLog(value = "创建 Webhook 渠道", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "infra", recordParams = false)
    @PostMapping("/webhooks")
    public Result<Void> createWebhook(@RequestBody Map<String, Object> body) {
        notifyService.createWebhook(text(body.get("name")), text(body.get("platform")), text(body.get("url")),
                text(body.get("secret")));
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_UPDATE)
    @AuditLog(value = "修改 Webhook 渠道", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra", recordParams = false)
    @PutMapping("/webhooks/{id}")
    public Result<Void> updateWebhook(@PathVariable long id, @RequestBody Map<String, Object> body) {
        notifyService.updateWebhook(id, text(body.get("name")), text(body.get("platform")), text(body.get("url")),
                text(body.get("secret")),
                body.containsKey("enabled") ? Boolean.parseBoolean(text(body.get("enabled"))) : null);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_DELETE)
    @AuditLog(value = "删除 Webhook 渠道", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "infra")
    @DeleteMapping("/webhooks/{id}")
    public Result<Void> deleteWebhook(@PathVariable long id) {
        notifyService.deleteWebhook(id);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_SEND)
    @AuditLog(value = "发送 Webhook 消息", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "infra")
    @PostMapping("/webhooks/{id}/send")
    public Result<NotifyService.RecordView> sendWebhook(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return Result.ok(notifyService.sendWebhook(id, text(body.get("templateCode")), params(body.get("params"))));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_QUERY)
    @GetMapping("/records/page")
    public Result<PageResult<NotifyService.RecordView>> recordPage(
            @RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String channel, @RequestParam(required = false) String status) {
        return Result.ok(notifyService.recordPage(current, size, channel, status));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_RETRY)
    @AuditLog(value = "重试发送记录", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra")
    @PostMapping("/records/{id}/retry")
    public Result<NotifyService.RecordView> retryRecord(@PathVariable long id) {
        return Result.ok(notifyService.retryRecord(id));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> params(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return (Map<String, String>) map;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
