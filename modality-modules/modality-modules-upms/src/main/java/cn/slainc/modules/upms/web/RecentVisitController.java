package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.dto.RecentVisitRequest;
import cn.slainc.modules.upms.entity.RecentVisitRecord;
import cn.slainc.modules.upms.service.RecentVisitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 自助接口只作用于当前登录账号，不设权限码；上报刻意不加审计日志——前端每次打开菜单都会触发，落日志会淹没日志表
@Tag(name = "最近访问", description = "当前登录账号最近打开的菜单或产品")
@RestController
@RequestMapping("/upms/recent-visit")
public class RecentVisitController {

    private final RecentVisitService recentVisitService;

    public RecentVisitController(RecentVisitService recentVisitService) {
        this.recentVisitService = recentVisitService;
    }

    @Operation(summary = "查询最近访问")
    @GetMapping
    public List<RecentVisitRecord> list(
            @Parameter(description = "返回条数，默认 20，最大 100") @RequestParam(required = false) Integer limit) {
        return recentVisitService.list(limit);
    }

    @Operation(summary = "记录一次访问", description = "同一目标重复上报只刷新访问时间")
    @PostMapping
    public RecentVisitRecord record(@RequestBody RecentVisitRequest request) {
        return recentVisitService.record(request);
    }
}
