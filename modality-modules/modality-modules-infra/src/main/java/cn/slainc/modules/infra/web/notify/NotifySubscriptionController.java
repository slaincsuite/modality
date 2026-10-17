package cn.slainc.modules.infra.web.notify;

import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.service.notify.NotifySubscriptionService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "消息订阅", description = "按服务配置接收渠道、接收人、免打扰与列表偏好")
@RestController
@RequestMapping("/infra/notify")
public class NotifySubscriptionController {

    private final NotifySubscriptionService subscriptionService;

    private final CurrentUser currentUser;

    public NotifySubscriptionController(NotifySubscriptionService subscriptionService, CurrentUser currentUser) {
        this.subscriptionService = subscriptionService;
        this.currentUser = currentUser;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_QUERY)
    @GetMapping("/subscriptions")
    public Result<List<NotifySubscriptionService.SubscriptionView>> subscriptions() {
        return Result.ok(subscriptionService.list(userId()));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_UPDATE)
    @AuditLog(value = "更新消息订阅", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra")
    @PutMapping("/subscriptions")
    public Result<List<NotifySubscriptionService.SubscriptionView>> updateSubscriptions(
            @RequestBody Object body) {
        return Result.ok(subscriptionService.update(userId(), commands(body)));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_UPDATE)
    @AuditLog(value = "关注服务助手", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra")
    @PostMapping("/subscriptions/assistant/follow")
    public Result<NotifySubscriptionService.PreferenceView> followAssistant() {
        return Result.ok(subscriptionService.followAssistant(userId()));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_QUERY)
    @GetMapping("/preferences")
    public Result<NotifySubscriptionService.PreferenceView> preferences() {
        return Result.ok(subscriptionService.preference(userId()));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTIFY_UPDATE)
    @AuditLog(value = "更新消息列表设置", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra")
    @PutMapping("/preferences")
    public Result<NotifySubscriptionService.PreferenceView> updatePreferences(@RequestBody Map<String, Object> body) {
        return Result.ok(subscriptionService.updatePreference(userId(), booleanOf(body.get("onlyUnverified")),
                booleanOf(body.get("hideMuted"))));
    }

    private long userId() {
        Long userId = currentUser.userId();
        BizAssert.notNull(userId, "当前登录用户不能为空");
        return userId;
    }

    private static List<NotifySubscriptionService.SubscriptionCommand> commands(Object body) {
        Object raw = body;
        if (body instanceof Map<?, ?> map && map.get("subscriptions") != null) {
            raw = map.get("subscriptions");
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(NotifySubscriptionController::command).toList();
        }
        return List.of(command(raw));
    }

    private static NotifySubscriptionService.SubscriptionCommand command(Object value) {
        BizAssert.isTrue(value instanceof Map<?, ?>, "订阅配置格式不正确");
        Map<?, ?> map = (Map<?, ?>) value;
        return new NotifySubscriptionService.SubscriptionCommand(longOf(map.get("id")),
                text(map.get("serviceCode")), text(map.get("serviceName")), channels(map.get("channels")),
                longOf(map.get("receiverId")), booleanOf(map.get("muted")));
    }

    private static List<String> channels(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value == null) {
            return null;
        }
        List<String> result = new ArrayList<>();
        result.add(String.valueOf(value));
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

    private static Long longOf(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
