package cn.slainc.services.livemonitor.web.stream;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.services.livemonitor.LiveMonitorKeys;
import cn.slainc.services.livemonitor.dto.LiveStreamRequest;
import cn.slainc.services.livemonitor.dto.LiveStreamStatsView;
import cn.slainc.services.livemonitor.dto.LiveStreamView;
import cn.slainc.services.livemonitor.service.stream.LiveStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "直播监控", description = "直播源维护与健康探测")
@RestController
@RequestMapping("/live-monitor")
public class LiveStreamController {

    private final LiveStreamService liveStreamService;

    public LiveStreamController(LiveStreamService liveStreamService) {
        this.liveStreamService = liveStreamService;
    }

    @Operation(summary = "分页查询直播源")
    @GetMapping("/streams/page")
    @RequirePermission(permissions = LiveMonitorKeys.PERMISSION_STREAM_QUERY)
    public Result<PageResult<LiveStreamView>> page(@RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Boolean enabled) {
        return Result.ok(liveStreamService.page(current, size, keyword, status, enabled));
    }

    @Operation(summary = "新增直播源")
    @PostMapping("/streams")
    @RequirePermission(permissions = LiveMonitorKeys.PERMISSION_STREAM_CREATE)
    @AuditLog(module = "直播监控", value = "新增直播源", operation = AuditOperation.CREATE, recordParams = false)
    public Result<LiveStreamView> create(@Valid @RequestBody LiveStreamRequest request) {
        return Result.ok(liveStreamService.create(request));
    }

    @Operation(summary = "修改直播源")
    @PutMapping("/streams/{id}")
    @RequirePermission(permissions = LiveMonitorKeys.PERMISSION_STREAM_UPDATE)
    @AuditLog(module = "直播监控", value = "修改直播源", operation = AuditOperation.UPDATE, recordParams = false)
    public Result<LiveStreamView> update(@PathVariable Long id,
                                         @Valid @RequestBody LiveStreamRequest request) {
        return Result.ok(liveStreamService.update(id, request));
    }

    @Operation(summary = "删除直播源")
    @DeleteMapping("/streams/{id}")
    @RequirePermission(permissions = LiveMonitorKeys.PERMISSION_STREAM_DELETE)
    @AuditLog(module = "直播监控", value = "删除直播源", operation = AuditOperation.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        liveStreamService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "探测单个直播源")
    @PostMapping("/streams/{id}/check")
    @RequirePermission(permissions = LiveMonitorKeys.PERMISSION_STREAM_CHECK)
    @AuditLog(module = "直播监控", value = "探测直播源", operation = AuditOperation.OTHER)
    public Result<LiveStreamView> check(@PathVariable Long id) {
        return Result.ok(liveStreamService.check(id));
    }

    @Operation(summary = "批量探测启用的直播源")
    @PostMapping("/streams/check-all")
    @RequirePermission(permissions = LiveMonitorKeys.PERMISSION_STREAM_CHECK)
    @AuditLog(module = "直播监控", value = "批量探测直播源", operation = AuditOperation.OTHER)
    public Result<LiveStreamStatsView> checkAll() {
        return Result.ok(liveStreamService.checkAll());
    }

    @Operation(summary = "查询直播源统计")
    @GetMapping("/stats")
    @RequirePermission(permissions = LiveMonitorKeys.PERMISSION_STREAM_QUERY)
    public Result<LiveStreamStatsView> stats() {
        return Result.ok(liveStreamService.stats());
    }
}
