package cn.slainc.modules.infra.service;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.storage.StorageTemplate;
import cn.slainc.storage.model.UploadRequest;
import cn.slainc.storage.model.UploadResult;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import cn.slainc.modules.infra.store.FileRecordStore;

public class FileUploadService {

    public record BatchResult(String name, boolean success, FileRecordStore.FileView record, String error) {
    }

    public record PresignView(String key, String url, long expiresInMinutes) {
    }

    private final StorageTemplate storageTemplate;

    private final FileRecordStore recordStore;

    private final CurrentUser currentUser;

    public FileUploadService(StorageTemplate storageTemplate, FileRecordStore recordStore, CurrentUser currentUser) {
        this.storageTemplate = storageTemplate;
        this.recordStore = recordStore;
        this.currentUser = currentUser;
    }

    public FileRecordStore.FileView upload(MultipartFile file, String folder, String md5) throws IOException {
        BizAssert.notNull(file, "上传文件不能为空");
        BizAssert.isTrue(!file.isEmpty(), "上传文件不能为空");
        UploadRequest request = UploadRequest.of(file.getOriginalFilename(), file.getContentType())
                .size(file.getSize())
                .md5(md5)
                .tenantId(TenantContext.getOrDefault())
                .uploaderId(currentUser.userId())
                .uploaderName(currentUser.operator())
                .custom("folder", folder == null ? "" : folder.trim());
        UploadResult result = storageTemplate.upload(file.getInputStream(), request);
        FileRecordStore.FileView view = new FileRecordStore.FileView(0, result.key(), file.getOriginalFilename(),
                folder == null ? "" : folder.trim(), result.contentType(), extension(file.getOriginalFilename()),
                result.size(), result.backend(), result.md5(), currentUser.userId(), currentUser.operator(),
                TenantContext.getOrDefault(), "NORMAL", System.currentTimeMillis(), System.currentTimeMillis());
        recordStore.insert(view);
        FileRecordStore.FileView stored = recordStore.findByKey(result.key());
        return stored == null ? view : stored;
    }

    public List<BatchResult> uploadBatch(MultipartFile[] files, String folder) throws IOException {
        BizAssert.isTrue(files != null && files.length > 0, "批量上传至少需要一个文件");
        List<BatchResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                results.add(new BatchResult(file.getOriginalFilename(), true, upload(file, folder, null), null));
            } catch (Exception exception) {
                results.add(new BatchResult(file.getOriginalFilename(), false, null, exception.getMessage()));
            }
        }
        return results;
    }

    public PresignView presignUpload(String filename, long ttlMinutes) {
        BizAssert.hasText(filename, "文件名不能为空");
        long safeTtl = Math.min(Math.max(ttlMinutes, 1), 1440);
        String url = storageTemplate.signedUploadUrl(filename, Duration.ofMinutes(safeTtl));
        return new PresignView(filename, url, safeTtl);
    }

    public Map<String, Object> instantUploadStats() {
        return Map.of(
                "attempts", storageTemplate.instantUploadStat().attempts(),
                "hits", storageTemplate.instantUploadStat().hits(),
                "hitRate", storageTemplate.instantUploadStat().hitRate(),
                "savedBytes", storageTemplate.instantUploadStat().savedBytes());
    }

    private static String extension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase();
    }
}
