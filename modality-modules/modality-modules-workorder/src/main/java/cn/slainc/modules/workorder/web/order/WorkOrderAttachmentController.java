package cn.slainc.modules.workorder.web.order;

import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.workorder.WorkorderKeys;
import cn.slainc.modules.workorder.service.order.WorkOrderAttachmentService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;


@Tag(name = "工单附件", description = "附件上传、列表与签名下载")
@RestController
@RequestMapping("/workorder/manage")
public class WorkOrderAttachmentController {

    private final WorkOrderAttachmentService attachmentService;

    public WorkOrderAttachmentController(WorkOrderAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/{id}/attachments")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ATTACHMENT_MANAGE)
    @AuditLog(value = "上传工单附件", operation = AuditOperation.CREATE, module = "workorder")
    public Object uploadAttachment(@PathVariable Long id, @RequestParam MultipartFile file) throws IOException {
        return attachmentService.upload(id, file.getOriginalFilename(), file.getBytes(), file.getContentType());
    }

    @GetMapping("/{id}/attachments")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_QUERY)
    public Object attachments(@PathVariable Long id) {
        return attachmentService.listByOrder(id);
    }

    @GetMapping("/attachments/{attachmentId}/download-url")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_QUERY)
    public Object attachmentDownloadUrl(@PathVariable Long attachmentId) {
        return attachmentService.downloadUrl(attachmentId);
    }

    @GetMapping("/attachments/{attachmentId}/download")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ORDER_QUERY)
    @AuditLog(value = "下载工单附件", operation = AuditOperation.EXPORT, module = "workorder")
    public void downloadAttachment(@PathVariable Long attachmentId, HttpServletResponse response) {
        attachmentService.download(attachmentId, response);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @RequirePermission(permissions = WorkorderKeys.PERMISSION_ATTACHMENT_MANAGE)
    @AuditLog(value = "删除工单附件", operation = AuditOperation.DELETE, module = "workorder")
    public Object deleteAttachment(@PathVariable Long attachmentId) {
        attachmentService.delete(attachmentId);
        return Map.of("deleted", true);
    }
}
