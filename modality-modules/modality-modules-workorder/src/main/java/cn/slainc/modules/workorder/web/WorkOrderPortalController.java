package cn.slainc.modules.workorder.web;

import cn.slainc.modules.workorder.service.WorkOrderService;
import cn.slainc.security.annotation.RequireLogin;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;


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
    @SuppressWarnings("unchecked")
    public Object create(@RequestBody Map<String, Object> body) {
        return workOrderService.create(body == null ? null : (String) body.get("title"),
                body == null ? null : (String) body.get("content"),
                body == null ? null : (String) body.get("priority"),
                body == null ? null : (Map<String, Object>) body.get("customFields"));
    }

    @GetMapping("/page")
    public Object page(@RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return workOrderService.pageMine(current, size);
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable Long id) {
        return workOrderService.requireOwnOrder(id);
    }

    @PostMapping("/{id}/close")
    public Object close(@PathVariable Long id) {
        return workOrderService.closeOwn(id);
    }
}
