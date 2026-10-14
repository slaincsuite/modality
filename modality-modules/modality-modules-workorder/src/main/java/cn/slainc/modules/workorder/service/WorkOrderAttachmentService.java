package cn.slainc.modules.workorder.service;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.workorder.entity.WorkOrderAttachmentRecord;
import cn.slainc.modules.workorder.mapper.WorkOrderAttachmentMapper;
import cn.slainc.storage.StorageTemplate;
import cn.slainc.storage.spi.UploadRequest;
import cn.slainc.storage.spi.UploadResult;
import cn.slainc.storage.url.SignedUrlService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.ByteArrayInputStream;
import java.util.List;


public class WorkOrderAttachmentService {

    private final StorageTemplate storageTemplate;

    private final WorkOrderAttachmentMapper attachmentMapper;

    public WorkOrderAttachmentService(StorageTemplate storageTemplate, WorkOrderAttachmentMapper attachmentMapper) {
        this.storageTemplate = storageTemplate;
        this.attachmentMapper = attachmentMapper;
    }

    public WorkOrderAttachmentRecord upload(Long orderId, String fileName, byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new BizException("附件内容不能为空");
        }
        String name = fileName == null || fileName.isBlank() ? "attachment" : fileName;
        UploadResult result = storageTemplate.upload(new ByteArrayInputStream(content),
                UploadRequest.of(name, contentType).size(content.length));
        WorkOrderAttachmentRecord record = new WorkOrderAttachmentRecord();
        record.setOrderId(orderId);
        record.setFileName(name);
        record.setObjectKey(result.key());
        record.setFileSize((long) content.length);
        record.setContentType(contentType);
        record.setUploaderId(UserContext.userId());
        record.setCreateTime(System.currentTimeMillis());
        attachmentMapper.insert(record);
        return record;
    }

    public List<WorkOrderAttachmentRecord> listByOrder(Long orderId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<WorkOrderAttachmentRecord>()
                .eq(WorkOrderAttachmentRecord::getOrderId, orderId)
                .orderByAsc(WorkOrderAttachmentRecord::getId));
    }

    public String downloadUrl(Long attachmentId) {
        WorkOrderAttachmentRecord record = requireAttachment(attachmentId);
        return storageTemplate.signedDownloadUrl(record.getObjectKey(), SignedUrlService.DEFAULT_TTL);
    }

    public void delete(Long attachmentId) {
        requireAttachment(attachmentId);
        attachmentMapper.deleteById(attachmentId);
    }

    private WorkOrderAttachmentRecord requireAttachment(Long attachmentId) {
        WorkOrderAttachmentRecord record = attachmentMapper.selectById(attachmentId);
        if (record == null) {
            throw new BizException("附件不存在或已删除");
        }
        return record;
    }
}
