package cn.slainc.modules.workorder.web.manage;

import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.workorder.dto.WorkOrderAutoAssignRequest;
import cn.slainc.modules.workorder.dto.WorkOrderBatchReopenRequest;
import cn.slainc.modules.workorder.WorkorderKeys;
import cn.slainc.modules.workorder.service.order.WorkOrderService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;


@Tag(name = "工单流转", description = "认领、转派、升级、挂起恢复与批量操作")
@RestController
@RequestMapping("/workorder/manage")
public class WorkOrderFlowController {

    private final WorkOrderService workOrderService;

    public WorkOrderFlowController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping("/{id}/claim")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_ASSIGN)
    @AuditLog(value = "认领工单", operation = AuditOperation.UPDATE, module = "workorder")
    public Object claim(@PathVariable Long id) {
        return workOrderService.claim(id);
    }

    @PostMapping("/{id}/transfer")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_ASSIGN)
    @AuditLog(value = "转派工单", operation = AuditOperation.UPDATE, module = "workorder")
    public Object transfer(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Number toAssigneeId = body == null ? null : (Number) body.get("toAssigneeId");
        return workOrderService.transfer(id, toAssigneeId == null ? null : toAssigneeId.longValue(),
                body == null ? null : (String) body.get("reason"));
    }

    @PostMapping("/{id}/escalate")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_ASSIGN)
    @AuditLog(value = "升级工单", operation = AuditOperation.UPDATE, module = "workorder")
    public Object escalate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Number toAssigneeId = body == null ? null : (Number) body.get("toAssigneeId");
        return workOrderService.escalate(id, toAssigneeId == null ? null : toAssigneeId.longValue(),
                body == null ? null : (String) body.get("reason"));
    }

    @PostMapping("/{id}/resolve")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_ASSIGN)
    @AuditLog(value = "解决工单", operation = AuditOperation.UPDATE, module = "workorder")
    public Object resolve(@PathVariable Long id) {
        return workOrderService.resolve(id);
    }

    @PostMapping("/{id}/suspend")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_ASSIGN)
    @AuditLog(value = "挂起工单", operation = AuditOperation.UPDATE, module = "workorder")
    public Object suspend(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return workOrderService.suspend(id, body == null ? null : (String) body.get("reason"));
    }

    @PostMapping("/{id}/resume")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_ASSIGN)
    @AuditLog(value = "恢复工单", operation = AuditOperation.UPDATE, module = "workorder")
    public Object resume(@PathVariable Long id) {
        return workOrderService.resume(id);
    }

    @PostMapping("/{id}/urge")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_ASSIGN)
    @AuditLog(value = "催办工单", operation = AuditOperation.OTHER, module = "workorder")
    public Object urge(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        workOrderService.urge(id, body == null ? null : (String) body.get("message"));
        return workOrderService.requireOrder(id);
    }

    @PostMapping("/{id}/auto-assign")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_ASSIGN)
    @AuditLog(value = "自动分派工单", operation = AuditOperation.UPDATE, module = "workorder")
    public Object autoAssign(@PathVariable Long id,
                             @RequestBody(required = false) WorkOrderAutoAssignRequest request) {
        WorkOrderAutoAssignRequest actual = request == null
                ? new WorkOrderAutoAssignRequest(null, null)
                : request;
        return workOrderService.autoAssign(id, actual.candidates(), actual.strategy());
    }

    @PostMapping("/{id}/reopen")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_ASSIGN)
    @AuditLog(value = "重开工单", operation = AuditOperation.UPDATE, module = "workorder")
    public Object reopen(@PathVariable Long id) {
        return workOrderService.reopen(id);
    }

    @PostMapping("/batch-reopen")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_ASSIGN)
    @AuditLog(value = "批量重开工单", operation = AuditOperation.UPDATE, module = "workorder")
    public Object batchReopen(@RequestBody(required = false) WorkOrderBatchReopenRequest request) {
        WorkOrderBatchReopenRequest actual = request == null
                ? new WorkOrderBatchReopenRequest(null, null, null, null, null)
                : request;
        return workOrderService.batchReopen(actual.ids(), actual.status(), actual.typeCode(),
                actual.from(), actual.to());
    }

    @GetMapping("/{id}/preview")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_QUERY)
    public Object preview(@PathVariable Long id, @RequestParam String action) {
        return workOrderService.preview(id, action);
    }

    @GetMapping("/{id}/activities")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_QUERY)
    public Object activities(@PathVariable Long id) {
        return workOrderService.activities(id);
    }
}
