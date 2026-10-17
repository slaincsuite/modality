package cn.slainc.modules.open.web.webhook;

import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.dto.WebhookSubscriptionRequest;
import cn.slainc.modules.open.service.webhook.WebhookDeliveryAdminService;
import cn.slainc.modules.open.service.webhook.WebhookSubscriptionAdminService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.Map;

@Tag(name = "Webhook 管理", description = "开放平台 Webhook 订阅与投递记录管理")
@RestController
@RequestMapping("/open/webhook")
public class WebhookController {

    private final WebhookSubscriptionAdminService subscriptionService;

    private final WebhookDeliveryAdminService deliveryService;

    public WebhookController(WebhookSubscriptionAdminService subscriptionService,
                             WebhookDeliveryAdminService deliveryService) {
        this.subscriptionService = subscriptionService;
        this.deliveryService = deliveryService;
    }

    @GetMapping("/page")
    @RequirePermission(permissions = OpenKeys.PERMISSION_WEBHOOK_QUERY)
    public Object page(@RequestParam(required = false) String name,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Boolean enabled,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return subscriptionService.page(name, keyword, enabled, current, size);
    }

    @GetMapping("/events")
    @RequirePermission(permissions = OpenKeys.PERMISSION_WEBHOOK_QUERY)
    public Object events() {
        return subscriptionService.events();
    }

    @GetMapping("/{id}")
    @RequirePermission(permissions = OpenKeys.PERMISSION_WEBHOOK_QUERY)
    public Object detail(@PathVariable Long id) {
        return subscriptionService.detail(id);
    }

    @PostMapping
    @RequirePermission(permissions = OpenKeys.PERMISSION_WEBHOOK_CREATE)
    @AuditLog(value = "新增 Webhook 订阅", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "open")
    public Object create(@RequestBody WebhookSubscriptionRequest request) {
        return subscriptionService.create(request);
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = OpenKeys.PERMISSION_WEBHOOK_UPDATE)
    @AuditLog(value = "修改 Webhook 订阅", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "open")
    public Object update(@PathVariable Long id, @RequestBody WebhookSubscriptionRequest request) {
        return subscriptionService.update(id, request);
    }

    @DeleteMapping("/{ids}")
    @RequirePermission(permissions = OpenKeys.PERMISSION_WEBHOOK_DELETE)
    @AuditLog(value = "删除 Webhook 订阅", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "open")
    public Object delete(@PathVariable List<Long> ids) {
        return subscriptionService.remove(ids);
    }

    @GetMapping("/{id}/deliveries")
    @RequirePermission(permissions = OpenKeys.PERMISSION_WEBHOOK_DELIVERY_QUERY)
    public Object deliveries(@PathVariable Long id,
                             @RequestParam(required = false) String eventType,
                             @RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "1") long current,
                             @RequestParam(defaultValue = "10") long size) {
        return deliveryService.page(id, eventType, status, current, size);
    }

    @GetMapping("/deliveries/page")
    @RequirePermission(permissions = OpenKeys.PERMISSION_WEBHOOK_DELIVERY_QUERY)
    public Object deliveriesPage(@RequestParam(required = false) Long subscriptionId,
                                 @RequestParam(required = false) String eventType,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(defaultValue = "1") long current,
                                 @RequestParam(defaultValue = "10") long size) {
        return deliveryService.page(subscriptionId, eventType, status, current, size);
    }

    @PostMapping("/deliveries/{id}/retry")
    @RequirePermission(permissions = OpenKeys.PERMISSION_WEBHOOK_DELIVERY_RETRY)
    @AuditLog(value = "手动重投 Webhook 投递", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "open")
    public Object retry(@PathVariable Long id) {
        return deliveryService.retry(id);
    }
}
