package cn.slainc.modules.workorder.web.portal;

import cn.slainc.core.context.UserContext;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.workorder.dto.WorkOrderSubscriptionView;
import cn.slainc.security.annotation.RequireLogin;
import cn.slainc.workorder.subscribe.WorkOrderSubscription;
import cn.slainc.workorder.subscribe.WorkOrderSubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;


@Tag(name = "工单订阅", description = "工单与类型的订阅管理")
@RestController
@RequestMapping("/workorder/my")
@RequireLogin
public class WorkOrderSubscriptionPortalController {

    private final WorkOrderSubscriptionService subscriptionService;

    public WorkOrderSubscriptionPortalController(WorkOrderSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/{id}/subscription")
    @AuditLog(value = "订阅工单", operation = AuditOperation.CREATE, module = "workorder")
    public Object subscribe(@PathVariable Long id) {
        subscriptionService.subscribeOrder(id, UserContext.userId());
        return Map.of("subscribed", true);
    }

    @DeleteMapping("/{id}/subscription")
    @AuditLog(value = "取消订阅工单", operation = AuditOperation.DELETE, module = "workorder")
    public Object unsubscribe(@PathVariable Long id) {
        return Map.of("unsubscribed", subscriptionService.unsubscribeOrder(id, UserContext.userId()));
    }

    @GetMapping("/{id}/subscription")
    public Object subscription(@PathVariable Long id) {
        WorkOrderSubscription subscription = subscriptionService
                .orderSubscription(id, UserContext.userId())
                .orElse(null);
        return WorkOrderSubscriptionView.of(subscription);
    }

    @GetMapping("/subscriptions")
    public Object typeSubscriptions() {
        return subscriptionService.myTypeSubscriptions(UserContext.userId());
    }

    @PostMapping("/type-subscriptions")
    @AuditLog(value = "订阅工单类型", operation = AuditOperation.CREATE, module = "workorder")
    public Object subscribeType(@RequestBody Map<String, String> body) {
        subscriptionService.subscribeType(body == null ? null : body.get("typeCode"), UserContext.userId());
        return Map.of("subscribed", true);
    }

    @DeleteMapping("/type-subscriptions/{typeCode}")
    @AuditLog(value = "取消订阅工单类型", operation = AuditOperation.DELETE, module = "workorder")
    public Object unsubscribeType(@PathVariable String typeCode) {
        subscriptionService.unsubscribeType(typeCode, UserContext.userId());
        return Map.of("unsubscribed", true);
    }
}
