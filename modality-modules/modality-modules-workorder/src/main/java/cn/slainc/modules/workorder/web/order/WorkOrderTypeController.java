package cn.slainc.modules.workorder.web.order;

import cn.slainc.modules.workorder.service.order.WorkOrderTypeService;
import cn.slainc.security.annotation.RequireLogin;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "工单类型", description = "工单类型字典")
@RestController
@RequestMapping("/workorder")
@RequireLogin
public class WorkOrderTypeController {

    private final WorkOrderTypeService typeService;

    public WorkOrderTypeController(WorkOrderTypeService typeService) {
        this.typeService = typeService;
    }

    @GetMapping("/types")
    public Object types() {
        return typeService.list();
    }
}
