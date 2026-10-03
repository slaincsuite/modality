package cn.slainc.modules.infra.service;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.storage.StorageTemplate;
import cn.slainc.storage.model.PartInfo;
import cn.slainc.storage.multipart.MultipartSession;
import cn.slainc.storage.multipart.MultipartUploadTemplate;
import cn.slainc.storage.support.StorageKeyGenerator;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import cn.slainc.modules.infra.store.FileRecordStore;

public class ChunkUploadService {

    public record ChunkInitView(String uploadId, String key, long partSize, int totalParts, long totalSize) {
    }

    public record ChunkProgress(int partNumber, int completedParts, int totalParts, long uploadedBytes,
                                long totalBytes, double percent) {
    }

    private record ChunkContext(MultipartSession session, String filename, String contentType, String folder) {
    }

    private final MultipartUploadTemplate multipartTemplate;

    private final FileRecordStore recordStore;

    private final CurrentUser currentUser;

    private final int contextMax;

    private final Map<String, ChunkContext> contexts = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ChunkContext> eldest) {
            return size() > contextMax;
        }
    };

    public ChunkUploadService(StorageTemplate storageTemplate, FileRecordStore recordStore, CurrentUser currentUser,
                              int contextMax) {
        this.multipartTemplate = new MultipartUploadTemplate(storageTemplate.storage());
        this.recordStore = recordStore;
        this.currentUser = currentUser;
        this.contextMax = Math.max(contextMax, 10);
    }

    public ChunkInitView init(String filename, long totalSize, String contentType, String folder) {
        BizAssert.hasText(filename, "文件名不能为空");
        BizAssert.isTrue(totalSize > 0, "文件总大小必须大于零");
        BizAssert.isTrue(multipartTemplate.supportsMultipart(), "当前存储后端不支持分片上传");
        MultipartSession session = multipartTemplate.init(StorageKeyGenerator.generate(filename), totalSize,
                contentType);
        String uploadId = session.uploadId();
        synchronized (contexts) {
            contexts.put(uploadId, new ChunkContext(session, filename, contentType, folder));
        }
        return new ChunkInitView(uploadId, session.key(), session.partSize(), session.totalParts(), totalSize);
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
        MultipartSession session = multipartTemplate.resume(context.session());
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
        multipartTemplate.complete(context.session());
        synchronized (contexts) {
            contexts.remove(uploadId);
        }
        MultipartSession session = context.session();
        String filename = context.filename();
        String ext = filename.lastIndexOf('.') < 0 ? null
                : filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        FileRecordStore.FileView view = new FileRecordStore.FileView(0, session.key(), filename,
                context.folder() == null ? "" : context.folder(), context.contentType(), ext, session.totalSize(),
                null, null, currentUser.userId(), currentUser.operator(), TenantContext.getOrDefault(), "NORMAL",
                System.currentTimeMillis(), System.currentTimeMillis());
        recordStore.insert(view);
        FileRecordStore.FileView stored = recordStore.findByKey(session.key());
        return stored == null ? view : stored;
    }

    public void abort(String uploadId) {
        ChunkContext context = context(uploadId);
        multipartTemplate.abort(context.session());
        synchronized (contexts) {
            contexts.remove(uploadId);
        }
    }

    private ChunkContext context(String uploadId) {
        BizAssert.hasText(uploadId, "上传任务编号不能为空");
        ChunkContext context;
        synchronized (contexts) {
            context = contexts.get(uploadId);
        }
        if (context == null) {
            throw new BizException("分片上传任务不存在或已失效（进程重启后任务丢失，请重新发起）");
        }
        return context;
    }
}
