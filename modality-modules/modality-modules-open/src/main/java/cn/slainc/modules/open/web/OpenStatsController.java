package cn.slainc.modules.open.web;

import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.service.OpenStatsService;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/admin/stats")
public class OpenStatsController {

    private final OpenStatsService statsService;

    public OpenStatsController(OpenStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    @RequirePermission(permissions = OpenKeys.PERMISSION_STATS_QUERY)
    public Object overview() {
        return statsService.overview();
    }
}
