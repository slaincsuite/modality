package cn.slainc.modules.infra.service;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.store.ChunkSession;
import cn.slainc.modules.infra.store.ChunkSessionStore;
import cn.slainc.modules.infra.store.FileRecordStore;
import cn.slainc.modules.infra.store.MemoryChunkSessionStore;
import cn.slainc.storage.StorageTemplate;
import cn.slainc.storage.spi.PartInfo;
import cn.slainc.storage.multipart.MultipartSession;
import cn.slainc.storage.multipart.MultipartUploadTemplate;
import cn.slainc.storage.support.StorageKeyGenerator;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public class ChunkUploadService {

    public record ChunkInitView(String uploadId, String key, long partSize, int totalParts, long totalSize) {
    }

    public record ChunkProgress(int partNumber, int completedParts, int totalParts, long uploadedBytes,
                                long totalBytes, double percent) {
    }

    private record ChunkContext(MultipartSession session, ChunkSession info) {
    }

    private final MultipartUploadTemplate multipartTemplate;

    private final FileRecordStore recordStore;

    private final CurrentUser currentUser;

    private final ChunkSessionStore sessionStore;

    public ChunkUploadService(StorageTemplate storageTemplate, FileRecordStore recordStore, CurrentUser currentUser,
                              int contextMax) {
        this(storageTemplate, recordStore, currentUser, new MemoryChunkSessionStore(contextMax));
    }

    public ChunkUploadService(StorageTemplate storageTemplate, FileRecordStore recordStore, CurrentUser currentUser,
                              ChunkSessionStore sessionStore) {
        this.multipartTemplate = new MultipartUploadTemplate(storageTemplate.storage());
        this.recordStore = recordStore;
        this.currentUser = currentUser;
        this.sessionStore = sessionStore == null ? new MemoryChunkSessionStore(100) : sessionStore;
    }

    public ChunkInitView init(String filename, long totalSize, String contentType, String folder) {
        BizAssert.hasText(filename, "文件名不能为空");
        BizAssert.isTrue(totalSize > 0, "文件总大小必须大于零");
        BizAssert.isTrue(multipartTemplate.supportsMultipart(), "当前存储后端不支持分片上传");
        MultipartSession session = multipartTemplate.init(StorageKeyGenerator.generate(filename), totalSize,
                contentType);
        ChunkSession info = new ChunkSession(session.uploadId(), session.key(), filename, contentType,
                folder == null ? "" : folder, session.partSize(), totalSize, owner());
        sessionStore.save(info);
        return new ChunkInitView(session.uploadId(), session.key(), session.partSize(), session.totalParts(),
                totalSize);
    }

    public ChunkProgress uploadPart(String uploadId, int partNumber, MultipartFile file) throws IOException {
        ChunkContext context = context(uploadId);
        BizAssert.notNull(file, "分片数据不能为空");
        PartInfo part = multipartTemplate.uploadPart(context.session(), partNumber, file.getInputStream(),
                file.getSize());
        MultipartSession session = context.session();
        return new ChunkProgress(part.partNumber(), session.uploadedPartCount(), session.totalParts(),
                session.uploadedBytes(), session.totalSize(),
                session.totalSize() == 0 ? 100 : session.uploadedBytes() * 100.0 / session.totalSize());
    }

    public Map<String, Object> parts(String uploadId) {
        ChunkContext context = context(uploadId);
        MultipartSession session = context.session();
        List<Map<String, Object>> uploaded = session.uploadedParts().stream()
                .map(part -> Map.<String, Object>of("partNumber", part.partNumber(), "size", part.size(),
                        "etag", part.etag() == null ? "" : part.etag()))
                .toList();
        return Map.of(
                "uploadId", uploadId,
                "key", session.key(),
                "uploadedParts", uploaded,
                "pendingPartNumbers", session.pendingPartNumbers(),
                "uploadedBytes", session.uploadedBytes(),
                "totalBytes", session.totalSize(),
                "percent", session.totalSize() == 0 ? 100 : session.uploadedBytes() * 100.0 / session.totalSize());
    }

    public FileRecordStore.FileView complete(String uploadId) {
        ChunkContext context = context(uploadId);
        MultipartSession session = context.session();
        ChunkSession info = context.info();
        multipartTemplate.complete(session);
        sessionStore.remove(uploadId);
        String filename = info.filename();
        String ext = filename.lastIndexOf('.') < 0 ? null
                : filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        FileRecordStore.FileView view = new FileRecordStore.FileView(0, session.key(), filename,
                info.folder() == null ? "" : info.folder(), info.contentType(), ext, session.totalSize(),
                null, null, currentUser.userId(), currentUser.operator(), TenantContext.getOrDefault(), "NORMAL",
                System.currentTimeMillis(), System.currentTimeMillis());
        recordStore.insert(view);
        FileRecordStore.FileView inserted = recordStore.findByKey(session.key());
        return inserted == null ? view : inserted;
    }

    public void abort(String uploadId) {
        ChunkContext context = context(uploadId);
        multipartTemplate.abort(context.session());
        sessionStore.remove(uploadId);
    }

    private ChunkContext context(String uploadId) {
        BizAssert.hasText(uploadId, "上传任务编号不能为空");
        ChunkSession info = sessionStore.find(uploadId);
        if (info == null) {
            throw new BizException("分片上传任务不存在或已失效，请重新发起");
        }
        if (!owner().equals(info.owner())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作该分片上传任务");
        }
        MultipartSession session = new MultipartSession(info.key(), info.uploadId(), info.totalSize(), info.partSize(),
                info.contentType());
        return new ChunkContext(multipartTemplate.resume(session), info);
    }

    private String owner() {
        Long userId = currentUser.userId();
        return userId == null ? currentUser.operator() : String.valueOf(userId);
    }
}
