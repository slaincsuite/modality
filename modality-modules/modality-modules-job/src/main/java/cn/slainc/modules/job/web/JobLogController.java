package cn.slainc.modules.job.web;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.job.JobKeys;
import cn.slainc.modules.job.entity.JobLogRecord;
import cn.slainc.modules.job.service.JobManageService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "任务日志", description = "任务执行日志查询与清理")
@RestController
@RequestMapping("/job/log")
public class JobLogController {

    private final JobManageService jobManageService;

    public JobLogController(JobManageService jobManageService) {
        this.jobManageService = jobManageService;
    }

    @Operation(summary = "分页查询任务执行日志")
    @GetMapping("/page")
    @RequirePermission(permissions = JobKeys.PERMISSION_LOG_QUERY)
    public Result<PageResult<JobLogRecord>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) String jobName) {
        return Result.ok(jobManageService.logs(current, size, jobName));
    }

    @Operation(summary = "按任务清理执行日志")
    @DeleteMapping("/{jobName}")
    @RequirePermission(permissions = JobKeys.PERMISSION_LOG_DELETE)
    @AuditLog(module = "任务日志", value = "按任务清理日志", operation = AuditOperation.DELETE)
    public Result<Integer> deleteByJob(@PathVariable String jobName) {
        return Result.ok(jobManageService.deleteLogsByJob(jobName));
    }

    @Operation(summary = "清理过期的执行日志")
    @DeleteMapping("/expired")
    @RequirePermission(permissions = JobKeys.PERMISSION_LOG_CLEAN)
    @AuditLog(module = "任务日志", value = "清理过期日志", operation = AuditOperation.DELETE)
    public Result<Integer> purgeExpired() {
        return Result.ok(jobManageService.purgeExpiredLogs());
    }
}
