package cn.slainc.modules.workorder.web.portal;

import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.workorder.dto.WorkOrderCreateRequest;
import cn.slainc.modules.workorder.dto.WorkOrderFilter;
import cn.slainc.modules.workorder.service.order.WorkOrderService;
import cn.slainc.security.annotation.RequireLogin;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "我的工单", description = "工单提交、回复与本人工单查询")
@RestController
@RequestMapping("/workorder/my")
@RequireLogin
public class WorkOrderPortalController {

    private final WorkOrderService workOrderService;

    public WorkOrderPortalController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping
    @AuditLog(value = "提交工单", operation = AuditOperation.CREATE, module = "workorder")
    public Object create(@RequestBody WorkOrderCreateRequest request) {
        WorkOrderCreateRequest actual = request == null
                ? new WorkOrderCreateRequest(null, null, null, null, null)
                : request;
        return workOrderService.create(actual.typeCode(), actual.title(), actual.content(),
                actual.priority(), actual.customFields());
    }

    @GetMapping("/page")
    public Object page(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String priority,
                       @RequestParam(required = false) String typeCode,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return workOrderService.pageMine(new WorkOrderFilter(status, priority, typeCode, keyword, from, to),
                current, size);
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable Long id) {
        return workOrderService.requireOwnOrder(id);
    }

    @PostMapping("/{id}/close")
    @AuditLog(value = "关闭本人工单", operation = AuditOperation.UPDATE, module = "workorder")
    public Object close(@PathVariable Long id) {
        return workOrderService.closeOwn(id);
    }
}
