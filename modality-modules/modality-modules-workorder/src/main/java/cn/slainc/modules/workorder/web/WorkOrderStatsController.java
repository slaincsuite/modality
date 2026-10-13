package cn.slainc.modules.workorder.web;

import cn.slainc.modules.workorder.WorkorderKeys;
import cn.slainc.modules.workorder.service.WorkOrderStatsService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "工单统计", description = "工单总览、分布与效率统计")
@RestController
@RequestMapping("/workorder/stats")
public class WorkOrderStatsController {

    private final WorkOrderStatsService statsService;

    public WorkOrderStatsController(WorkOrderStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_STATS_QUERY)
    public Object overview() {
        return statsService.overview();
    }

    @GetMapping("/by-status")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_STATS_QUERY)
    public Object byStatus() {
        return statsService.byStatus();
    }

    @GetMapping("/by-priority")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_STATS_QUERY)
    public Object byPriority() {
        return statsService.byPriority();
    }

    @GetMapping("/by-type")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_STATS_QUERY)
    public Object byType() {
        return statsService.byType();
    }

    @GetMapping("/assignee-ranking")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_STATS_QUERY)
    public Object assigneeRanking(@RequestParam(defaultValue = "10") int limit) {
        return statsService.assigneeRanking(limit);
    }

    @GetMapping("/avg-resolution")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_STATS_QUERY)
    public Object avgResolution() {
        return statsService.avgResolutionMillis();
    }
}
