package cn.slainc.modules.infra.web.notify;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.service.notify.NoticeService;
import cn.slainc.security.annotation.RequireLogin;
import cn.slainc.security.annotation.RequirePermission;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "通知公告", description = "公告发布与查询")
@RestController
@RequestMapping("/infra/notice")
public class NoticeController {

    private final NoticeService noticeService;

    private final CurrentUser currentUser;

    public NoticeController(NoticeService noticeService, CurrentUser currentUser) {
        this.noticeService = noticeService;
        this.currentUser = currentUser;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTICE_QUERY)
    @GetMapping("/page")
    public Result<PageResult<NoticeService.NoticeView>> page(@RequestParam(defaultValue = "1") long current,
                                                              @RequestParam(defaultValue = "10") long size,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) String status) {
        return Result.ok(noticeService.page(current, size, keyword, status));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_NOTICE_QUERY)
    @GetMapping("/{id}")
    public Result<NoticeService.NoticeView> detail(@PathVariable long id) {
        return Result.ok(noticeService.find(id));
    }

    @RequireLogin
    @GetMapping("/active")
    public Result<List<NoticeService.NoticeView>> active() {
        return Result.ok(noticeService.activeNotices());
    }

    @cn.slainc.security.annotation.RequirePermission(permissions = InfraKeys.PERMISSION_NOTICE_CREATE)
    @AuditLog(value = "创建通知公告", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "infra")
    @PostMapping
    public Result<NoticeService.NoticeView> create(@RequestBody Map<String, Object> body) {
        return Result.ok(noticeService.create(text(body.get("title")), text(body.get("content")),
                text(body.get("level")), longOrNull(body.get("expireTime")), currentUser.operator()));
    }

    @cn.slainc.security.annotation.RequirePermission(permissions = InfraKeys.PERMISSION_NOTICE_UPDATE)
    @AuditLog(value = "修改通知公告", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra")
    @PutMapping("/{id}")
    public Result<NoticeService.NoticeView> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return Result.ok(noticeService.update(id, text(body.get("title")), text(body.get("content")),
                text(body.get("level")), longOrNull(body.get("expireTime"))));
    }

    @cn.slainc.security.annotation.RequirePermission(permissions = InfraKeys.PERMISSION_NOTICE_UPDATE)
    @AuditLog(value = "发布通知公告", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra")
    @PostMapping("/{id}/publish")
    public Result<NoticeService.NoticeView> publish(@PathVariable long id) {
        return Result.ok(noticeService.publish(id));
    }

    @cn.slainc.security.annotation.RequirePermission(permissions = InfraKeys.PERMISSION_NOTICE_UPDATE)
    @AuditLog(value = "下线通知公告", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "infra")
    @PostMapping("/{id}/offline")
    public Result<NoticeService.NoticeView> offline(@PathVariable long id) {
        return Result.ok(noticeService.offline(id));
    }

    @cn.slainc.security.annotation.RequirePermission(permissions = InfraKeys.PERMISSION_NOTICE_DELETE)
    @AuditLog(value = "删除通知公告", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "infra")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        noticeService.delete(id);
        return Result.ok();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longOrNull(Object value) {
        return value == null ? null : Long.parseLong(String.valueOf(value));
    }
}
