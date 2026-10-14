package cn.slainc.modules.infra.web;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.audit.AuditCategory;
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.logger.audit.AuditOperation;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.entity.RecycleItemEntity;
import cn.slainc.modules.infra.service.RecycleBinService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "回收站", description = "删除快照的分页查询、恢复与彻底删除")
@RestController
@RequestMapping("/infra/recycle")
public class RecycleBinController {

    private final RecycleBinService recycleBinService;

    public RecycleBinController(RecycleBinService recycleBinService) {
        this.recycleBinService = recycleBinService;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_RECYCLE_QUERY)
    @GetMapping
    public Result<PageResult<RecycleItemEntity>> page(@RequestParam(required = false) String entityType,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String operator,
                                                      @RequestParam(required = false) Boolean restored,
                                                      @RequestParam(required = false) Long beginTime,
                                                      @RequestParam(required = false) Long endTime,
                                                      @RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "10") long size) {
        return Result.ok(recycleBinService.page(entityType, keyword, operator, restored, beginTime, endTime,
                current, size));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_RECYCLE_QUERY)
    @GetMapping("/{id}")
    public Result<RecycleItemEntity> detail(@PathVariable long id) {
        return Result.ok(recycleBinService.get(id));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_RECYCLE_RESTORE)
    @AuditLog(value = "恢复回收站记录", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra")
    @PostMapping("/{id}/restore")
    public Result<RecycleItemEntity> restore(@PathVariable long id) {
        return Result.ok(recycleBinService.restore(id));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_RECYCLE_PURGE)
    @AuditLog(value = "彻底删除回收站记录", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "infra")
    @DeleteMapping("/{id}")
    public Result<Boolean> purge(@PathVariable long id) {
        return Result.ok(recycleBinService.purge(id));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_RECYCLE_PURGE)
    @AuditLog(value = "清理过期回收站记录", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "infra")
    @PostMapping("/clean")
    public Result<Integer> clean() {
        return Result.ok(recycleBinService.purgeExpired());
    }
}
