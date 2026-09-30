package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.UpmsKeys;

import cn.slainc.modules.upms.service.UserStatsService;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/upms/stats")
public class UserStatsController {

    private final UserStatsService statsService;

    public UserStatsController(UserStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_STATS_QUERY)
    public Map<String, Object> overview() {
        return statsService.overview();
    }

    @GetMapping("/new-user-trend")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_STATS_QUERY)
    public List<Map<String, Object>> newUserTrend(@RequestParam(required = false) Integer days) {
        return statsService.newUserTrend(days == null ? 0 : days);
    }

    @GetMapping("/role-distribution")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_STATS_QUERY)
    public List<Map<String, Object>> roleDistribution() {
        return statsService.roleDistribution();
    }
}
