package cn.slainc.modules.infra.web.notify;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.service.notify.NotifyReceiverService;
import cn.slainc.redis.ratelimit.RateLimit;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
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

@Tag(name = "通知接收人", description = "接收人、联系方式、验证状态与免打扰偏好")
@RestController
@RequestMapping("/infra/notify/receivers")
public class NotifyReceiverController {

    private final NotifyReceiverService receiverService;

    public NotifyReceiverController(NotifyReceiverService receiverService) {
        this.receiverService = receiverService;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_QUERY)
    @GetMapping("/page")
    public Result<PageResult<NotifyReceiverService.ReceiverView>> page(
            @RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String type) {
        return Result.ok(receiverService.page(current, size, keyword, type));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_CREATE)
    @AuditLog(value = "创建通知接收人", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "infra", recordParams = false)
    @PostMapping
    public Result<NotifyReceiverService.ReceiverView> create(@RequestBody Map<String, Object> body) {
        return Result.ok(receiverService.create(new NotifyReceiverService.ReceiverCreateCommand(
                text(body.get("name")), text(body.get("type")), text(body.get("accountId")),
                text(body.get("phone")), text(body.get("email")),
                firstText(body.get("feishuId"), body.get("feishu"), body.get("feishuAccount"),
                        body.get("wecomId")))));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_DELETE)
    @AuditLog(value = "删除通知接收人", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "infra")
    @DeleteMapping("/{ids}")
    public Result<Integer> delete(@PathVariable List<Long> ids) {
        return Result.ok(receiverService.delete(ids));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_UPDATE)
    @AuditLog(value = "发送接收人验证链接", operation = AuditOperation.UPDATE, category = AuditCategory.SECURITY,
            module = "infra", recordParams = false)
    @RateLimit(limit = 5, windowSeconds = 60, message = "验证链接发送过于频繁，请稍后重试")
    @PostMapping("/{id}/verify")
    public Result<NotifyReceiverService.VerifyResult> verify(
            @PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> safeBody = body == null ? Map.of() : body;
        return Result.ok(receiverService.verify(id, channels(safeBody), text(safeBody.get("token"))));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_UPDATE)
    @AuditLog(value = "修改接收人联系方式", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra", recordParams = false)
    @PutMapping("/{id}/contacts")
    public Result<NotifyReceiverService.ReceiverView> updateContacts(@PathVariable long id,
                                                                    @RequestBody Map<String, Object> body) {
        return Result.ok(receiverService.updateContacts(id, new NotifyReceiverService.ContactCommand(
                text(body.get("phone")), text(body.get("email")),
                firstText(body.get("feishuId"), body.get("feishu"), body.get("feishuAccount"),
                        body.get("wecomId")))));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_UPDATE)
    @AuditLog(value = "修改接收人免打扰偏好", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra")
    @PutMapping("/{id}/preferences")
    public Result<NotifyReceiverService.ReceiverView> updatePreferences(@PathVariable long id,
                                                                        @RequestBody Map<String, Object> body) {
        return Result.ok(receiverService.updatePreferences(id, new NotifyReceiverService.PreferenceCommand(
                booleanOf(body.get("nightReceive")), booleanOf(body.get("smsMute")),
                booleanOf(body.get("marketingMute")))));
    }

    private static List<String> channels(Map<String, Object> body) {
        Object raw = body.get("channels");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        List<String> result = new ArrayList<>();
        String channel = text(body.get("channel"));
        if (channel != null && !channel.isBlank()) {
            result.add(channel);
        }
        return result;
    }

    private static Boolean booleanOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
