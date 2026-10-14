package cn.slainc.modules.infra.service;

import cn.slainc.core.lang.BizAssert;
import cn.slainc.storage.StorageTemplate;
import cn.slainc.storage.spi.StorageMetadata;
import cn.slainc.storage.preview.FilePreviewService;
import cn.slainc.storage.preview.FilePreviewService.PreviewResult;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.util.UriUtils;
import cn.slainc.modules.infra.store.FileRecordStore;

public class FileManagerService {

    public record SignedUrlView(long id, String url, String method, long expiresAtEpochMilli) {
    }

    public record DetailView(FileRecordStore.FileView record, Long contentLength, String storageContentType,
                             Integer imageWidth, Integer imageHeight) {
    }

    private final StorageTemplate storageTemplate;

    private final FileRecordStore recordStore;

    private final FilePreviewService previewService;

    private final int previewTextMaxBytes;

    public FileManagerService(StorageTemplate storageTemplate, FileRecordStore recordStore,
                              FilePreviewService previewService, int previewTextMaxBytes) {
        this.storageTemplate = storageTemplate;
        this.recordStore = recordStore;
        this.previewService = previewService;
        this.previewTextMaxBytes = previewTextMaxBytes;
    }

    public FileRecordStore.FileView find(long id) {
        FileRecordStore.FileView view = recordStore.find(id);
        BizAssert.found(view != null, "文件记录不存在");
        return view;
    }

    public cn.slainc.core.result.PageResult<FileRecordStore.FileView> page(long current, long size, String keyword,
                                                                           String folder, String contentType,
                                                                           Long uploaderId, Long from, Long to) {
        return recordStore.page(current, size, keyword, folder, contentType, uploaderId, from, to);
    }

    public DetailView detail(long id) {
        FileRecordStore.FileView view = find(id);
        StorageMetadata metadata = storageTemplate.metadataQuietly(view.fileKey()).orElse(null);
        return new DetailView(view, metadata == null ? null : metadata.size(),
                metadata == null ? null : metadata.contentType(),
                metadata == null || metadata.image() == null ? null : metadata.image().width(),
                metadata == null || metadata.image() == null ? null : metadata.image().height());
    }

    public void download(long id, OutputStream target) throws IOException {
        FileRecordStore.FileView view = find(id);
        storageTemplate.download(view.fileKey(), target);
    }

    public SignedUrlView signedUrl(long id, long ttlMinutes) {
        FileRecordStore.FileView view = find(id);
        long safeTtl = Math.min(Math.max(ttlMinutes, 1), 1440);
        String url = storageTemplate.signedDownloadUrl(view.fileKey(), Duration.ofMinutes(safeTtl));
        return new SignedUrlView(id, url, "GET", System.currentTimeMillis() + safeTtl * 60000);
    }

    public void recycle(long id) {
        FileRecordStore.FileView view = find(id);
        BizAssert.state(!"RECYCLED".equals(view.status()), "文件已在回收站");
        recordStore.updateStatus(id, "RECYCLED");
    }

    public void restore(long id) {
        FileRecordStore.FileView view = find(id);
        BizAssert.state("RECYCLED".equals(view.status()), "文件不在回收站，无需恢复");
        recordStore.updateStatus(id, "NORMAL");
    }

    public void purge(long id) {
        FileRecordStore.FileView view = find(id);
        storageTemplate.delete(view.fileKey());
        recordStore.delete(id);
    }

    public Map<String, Object> batchDelete(java.util.List<Long> ids, boolean purge) {
        BizAssert.notEmpty(ids, "文件编号列表不能为空");
        int recycled = 0;
        int purged = 0;
        Map<String, String> failures = new LinkedHashMap<>();
        for (Long id : ids) {
            try {
                if (purge) {
                    purge(id);
                    purged++;
                } else {
                    recycle(id);
                    recycled++;
                }
            } catch (Exception exception) {
                failures.put(String.valueOf(id), exception.getMessage());
            }
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("recycled", recycled);
        summary.put("purged", purged);
        summary.put("failed", failures.size());
        summary.put("errors", failures);
        return summary;
    }

    public void rename(long id, String name) {
        BizAssert.hasText(name, "文件名不能为空");
        find(id);
        recordStore.updateNameOrFolder(id, name, null);
    }

    public void move(long id, String folder) {
        find(id);
        recordStore.updateNameOrFolder(id, null, folder == null ? "" : folder.trim());
    }

    public DownloadPayload preview(long id) throws IOException {
        FileRecordStore.FileView view = find(id);
        String declared = view.contentType() == null || view.contentType().isBlank()
                ? cn.slainc.storage.support.ContentTypes.detect(view.name()) : view.contentType();
        String contentType = PreviewGuard.requireInlineAllowed(declared);
        if ("text/plain".equals(contentType)) {
            byte[] data;
            try (java.io.InputStream input = storageTemplate.download(view.fileKey())) {
                data = input.readNBytes(previewTextMaxBytes);
            }
            PreviewGuard.assertMagicMatches(contentType, data);
            return new DownloadPayload("text/plain; charset=UTF-8", data, view.name(), true);
        }
        PreviewResult result = previewService.preview(view.fileKey(), contentType);
        if (result.kind() == PreviewResult.Kind.INLINE) {
            PreviewGuard.assertMagicMatches(contentType, result.binary());
            return new DownloadPayload(contentType, result.binary(), view.name(), true);
        }
        throw new cn.slainc.core.exception.BizException(cn.slainc.core.result.ResultCode.BUSINESS_ERROR,
                "该文件类型暂不支持预览：" + contentType + "（" + result.reason() + "）");
    }

    public String encodeFilename(String name) {
        return UriUtils.encode(name, java.nio.charset.StandardCharsets.UTF_8);
    }

    public record DownloadPayload(String contentType, byte[] data, String filename, boolean inline) {
    }
}
