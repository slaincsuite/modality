package cn.slainc.modules.job.web.task;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.idempotent.annotation.Idempotent;
import cn.slainc.job.support.JobTriggerAck;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.job.JobKeys;
import cn.slainc.modules.job.entity.JobTaskRecord;
import cn.slainc.modules.job.service.task.JobManageService;
import cn.slainc.redis.ratelimit.RateLimit;
import cn.slainc.security.annotation.RequirePermission;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "定时任务", description = "任务定义维护与手动触发")
@RestController
@RequestMapping("/job/task")
public class JobTaskController {

    private final JobManageService jobManageService;

    public JobTaskController(JobManageService jobManageService) {
        this.jobManageService = jobManageService;
    }

    @Operation(summary = "分页查询任务定义")
    @GetMapping("/page")
    @RequirePermission(permissions = JobKeys.PERMISSION_TASK_QUERY)
    public Result<PageResult<JobTaskRecord>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String jobGroup) {
        return Result.ok(jobManageService.page(current, size, keyword, jobGroup));
    }

    @Operation(summary = "查询任务定义详情")
    @GetMapping("/{id}")
    @RequirePermission(permissions = JobKeys.PERMISSION_TASK_QUERY)
    public Result<JobTaskRecord> detail(@PathVariable Long id) {
        return Result.ok(jobManageService.get(id));
    }

    @Operation(summary = "新增任务定义")
    @PostMapping
    @RequirePermission(permissions = JobKeys.PERMISSION_TASK_CREATE)
    @Idempotent
    @AuditLog(module = "定时任务", value = "新增任务", operation = AuditOperation.CREATE)
    public Result<JobTaskRecord> create(@Valid @RequestBody JobTaskRecord entity) {
        return Result.ok(jobManageService.create(entity));
    }

    @Operation(summary = "修改任务定义")
    @PutMapping("/{id}")
    @RequirePermission(permissions = JobKeys.PERMISSION_TASK_UPDATE)
    @Idempotent
    @AuditLog(module = "定时任务", value = "修改任务", operation = AuditOperation.UPDATE)
    public Result<JobTaskRecord> update(@PathVariable Long id, @Valid @RequestBody JobTaskRecord entity) {
        entity.setId(id);
        return Result.ok(jobManageService.update(entity));
    }

    @Operation(summary = "启用或停用任务")
    @PutMapping("/{id}/status")
    @RequirePermission(permissions = JobKeys.PERMISSION_TASK_UPDATE)
    @AuditLog(module = "定时任务", value = "变更任务状态", operation = AuditOperation.CONFIG)
    public Result<JobTaskRecord> changeStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.ok(jobManageService.changeStatus(id, enabled));
    }

    @Operation(summary = "立即触发一次任务")
    @PostMapping("/{id}/trigger")
    @RequirePermission(permissions = JobKeys.PERMISSION_TASK_TRIGGER)
    @RateLimit(limit = 30, windowSeconds = 60, message = "任务触发过于频繁，请稍后重试")
    @AuditLog(module = "定时任务", value = "手动触发任务", operation = AuditOperation.OTHER)
    public Result<Map<String, Object>> trigger(@PathVariable Long id,
                                               @RequestBody(required = false) Map<String, Object> params) {
        JobTriggerAck ack = jobManageService.trigger(id, params == null ? Map.of() : params);
        return Result.ok(Map.of("traceId", String.valueOf(ack.traceId()), "jobName",
                String.valueOf(ack.jobName()), "accepted", ack.accepted(), "message", ack.message()));
    }

    @Operation(summary = "删除任务定义")
    @DeleteMapping("/{id}")
    @RequirePermission(permissions = JobKeys.PERMISSION_TASK_DELETE)
    @AuditLog(module = "定时任务", value = "删除任务", operation = AuditOperation.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        jobManageService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "查看任务运行时状态")
    @GetMapping("/status/{jobName}")
    @RequirePermission(permissions = JobKeys.PERMISSION_TASK_QUERY)
    public Result<Map<String, Object>> status(@PathVariable String jobName) {
        return Result.ok(jobManageService.status(jobName));
    }

    @Operation(summary = "列出已注册的处理器")
    @GetMapping("/handlers")
    @RequirePermission(permissions = JobKeys.PERMISSION_TASK_QUERY)
    public Result<List<String>> handlers() {
        return Result.ok(new ArrayList<>(jobManageService.registeredHandlers().keySet()));
    }

    @Operation(summary = "预览 Cron 的下几次执行时间")
    @GetMapping("/cron/next")
    @RequirePermission(permissions = JobKeys.PERMISSION_TASK_PREVIEW)
    public Result<List<String>> nextExecutions(@RequestParam String cron,
                                               @RequestParam(defaultValue = "5") int count) {
        return Result.ok(jobManageService.nextExecutions(cron, count));
    }
}
