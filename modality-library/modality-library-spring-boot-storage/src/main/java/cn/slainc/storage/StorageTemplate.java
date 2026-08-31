package cn.slainc.storage;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.storage.archive.ZipStreamPacker;
import cn.slainc.storage.dedup.DedupWrapper;
import cn.slainc.storage.dedup.InstantUploadService;
import cn.slainc.storage.dedup.InstantUploadStats;
import cn.slainc.storage.metadata.ImageMetadataReader;
import cn.slainc.storage.metadata.MetadataStore;
import cn.slainc.storage.metadata.SidecarMetadataStore;
import cn.slainc.storage.metrics.StorageMetricsRecorder;
import cn.slainc.storage.model.StorageMetadata;
import cn.slainc.storage.model.StorageObject;
import cn.slainc.storage.model.UploadRequest;
import cn.slainc.storage.model.UploadResult;
import cn.slainc.storage.quota.StorageQuotaService;
import cn.slainc.storage.routing.RoutingFileStorage;
import cn.slainc.storage.routing.StorageBackendRegistry;
import cn.slainc.storage.support.ContentTypes;
import cn.slainc.storage.support.StorageKeyGenerator;
import cn.slainc.storage.url.SignedUrlService;
import cn.slainc.utils.io.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class StorageTemplate {

    private static final Logger log = LoggerFactory.getLogger(StorageTemplate.class);

    private final RoutingFileStorage storage;

    private final StorageBackendRegistry registry;

    private final StorageQuotaService quotaService;

    private final StorageMetricsRecorder metrics;

    private final DedupWrapper dedupWrapper;

    private final SignedUrlService signedUrlService;

    private final MetadataStore metadataStore;

    private final InstantUploadStats instantUploadStats = new InstantUploadStats();

    public StorageTemplate(RoutingFileStorage storage, StorageBackendRegistry registry,
                           StorageQuotaService quotaService, StorageMetricsRecorder metrics,
                           DedupWrapper dedupWrapper, SignedUrlService signedUrlService) {
        this(storage, registry, quotaService, metrics, dedupWrapper, signedUrlService,
                new SidecarMetadataStore(storage));
    }

    public StorageTemplate(RoutingFileStorage storage, StorageBackendRegistry registry,
                           StorageQuotaService quotaService, StorageMetricsRecorder metrics,
                           DedupWrapper dedupWrapper, SignedUrlService signedUrlService,
                           MetadataStore metadataStore) {
        this.metadataStore = metadataStore == null ? MetadataStore.NOOP : metadataStore;
        this.storage = storage;
        this.registry = registry;
        this.quotaService = quotaService;
        this.metrics = metrics == null ? StorageMetricsRecorder.NOOP : metrics;
        this.dedupWrapper = dedupWrapper;
        this.signedUrlService = signedUrlService;
    }

    public RoutingFileStorage storage() {
        return storage;
    }

    public String activeBackend() {
        return storage.activeBackend();
    }

    public UploadResult upload(InputStream data, UploadRequest request) {
        UploadRequest actual = request == null ? new UploadRequest() : request;
        String key = actual.key() == null || actual.key().isBlank()
                ? StorageKeyGenerator.generate(actual.filename())
                : actual.key();
        String contentType = actual.contentType() == null ? ContentTypes.detect(actual.filename()) : actual.contentType();
        Long tenantId = actual.tenantId() == null ? TenantContext.getOrDefault() : actual.tenantId();
        Long userId = actual.uploaderId() == null ? UserContext.userId() : actual.uploaderId();
        if (quotaService != null) {
            quotaService.checkUpload(tenantId, userId, actual.size());
        }
        InstantUploadService dedup = dedupService();
        String secondaryName = secondaryBackend();
        java.nio.file.Path spool = null;
        InputStream primary = data;
        if (secondaryName != null) {
            try {
                spool = java.nio.file.Files.createTempFile("storage-dual-", ".tmp");
                java.nio.file.Files.copy(data, spool, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                primary = java.nio.file.Files.newInputStream(spool);
            } catch (java.io.IOException ex) {
                throw new StorageException("双写暂存上传内容失败：" + key, ex);
            }
        }
        UploadResult result;
        try {
            if (dedup != null) {
                result = dedup.upload(key, primary, actual.size(), contentType, actual.md5());
            } else {
                storage.upload(key, primary, actual.size(), contentType);
                result = new UploadResult(key, storage.activeBackend(), actual.size(), actual.md5(), contentType,
                        false, 1, Instant.now());
            }
            writeSecondary(secondaryName, spool, result, contentType);
        } finally {
            closeQuietly(primary);
            deleteQuietly(spool);
        }
        metrics.recordInstantUpload(result.instant());
        instantUploadStats.recordAttempt();
        if (result.instant()) {
            instantUploadStats.recordHit(result.size());
        }
        if (quotaService != null && !result.instant() && actual.size() > 0) {
            quotaService.record(tenantId, userId, actual.size());
        }
        metadataStore.save(key, StorageMetadata.builder(key)
                .size(result.size())
                .contentType(contentType)
                .md5(result.md5())
                .createdAt(result.createdAt())
                .tenantId(tenantId)
                .uploaderId(userId)
                .uploaderName(actual.uploaderName() == null ? UserContext.username() : actual.uploaderName())
                .custom(actual.custom())
                .build());
        log.debug("对象上传完成：key={}，后端={}，秒传={}", result.key(), result.backend(), result.instant());
        return result;
    }

    public InputStream download(String key) {
        return storage.download(key);
    }

    public void download(String key, OutputStream target) {
        storage.download(key, target);
    }

    public boolean delete(String key) {
        InstantUploadService dedup = dedupService();
        boolean deleted = dedup != null ? dedup.delete(key) : storage.delete(key);
        metadataStore.delete(key);
        String secondaryName = secondaryBackend();
        if (secondaryName != null) {
            registry.find(secondaryName).ifPresent(secondary -> {
                try {
                    secondary.delete(key);
                } catch (RuntimeException ex) {
                    log.warn("删除过渡期副后端对象失败：{}，原因：{}", key, ex.getMessage());
                }
            });
        }
        if (deleted && quotaService != null) {
            try {
                StorageMetadata metadata = metadata(key);
                quotaService.record(metadata.tenantId(), metadata.uploaderId(), -metadata.size());
            } catch (RuntimeException ex) {
                log.debug("回收配额失败，对象可能已被删除：{}", key);
            }
        }
        return deleted;
    }

    public boolean exists(String key) {
        return storage.exists(key);
    }

    public List<StorageObject> list(String prefix) {
        return storage.list(prefix).stream()
                .filter(object -> !metadataStore.internal(object.key()))
                .toList();
    }

    public StorageMetadata metadata(String key) {
        StorageMetadata metadata = storage.metadata(key);
        StorageMetadata sidecar = metadataStore.load(key).orElse(null);
        if (sidecar != null) {
            metadata = new StorageMetadata(metadata.key(), metadata.size(), metadata.contentType(), metadata.md5(),
                    metadata.etag(), sidecar.createdAt() == null ? metadata.createdAt() : sidecar.createdAt(),
                    metadata.lastModified(),
                    sidecar.tenantId() == null ? metadata.tenantId() : sidecar.tenantId(),
                    sidecar.uploaderId() == null ? metadata.uploaderId() : sidecar.uploaderId(),
                    sidecar.uploaderName() == null ? metadata.uploaderName() : sidecar.uploaderName(),
                    sidecar.custom(), metadata.image(), metadata.video());
        }
        StorageMetadata.Builder builder = StorageMetadata.builder(metadata.key())
                .size(metadata.size())
                .contentType(metadata.contentType())
                .md5(metadata.md5())
                .etag(metadata.etag())
                .createdAt(metadata.createdAt())
                .lastModified(metadata.lastModified())
                .tenantId(metadata.tenantId() == null ? TenantContext.getOrDefault() : metadata.tenantId())
                .uploaderId(metadata.uploaderId() == null ? UserContext.userId() : metadata.uploaderId())
                .uploaderName(metadata.uploaderName() == null ? UserContext.username() : metadata.uploaderName())
                .custom(metadata.custom());
        if (ContentTypes.isImage(metadata.contentType())) {
            try (InputStream input = storage.download(key)) {
                builder.image(ImageMetadataReader.read(IoUtils.readBytes(input)));
            } catch (IOException | RuntimeException ex) {
                log.debug("读取图片元数据失败，已跳过：{}，原因：{}", key, ex.getMessage());
            }
        }
        return builder.build();
    }

    public Optional<StorageMetadata> metadataQuietly(String key) {
        try {
            return Optional.of(metadata(key));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public String signedDownloadUrl(String key, Duration ttl) {
        return signedUrlService.downloadUrl(key, ttl).url();
    }

    public String signedUploadUrl(String key, Duration ttl) {
        return signedUrlService.uploadUrl(key, ttl).url();
    }

    public long zip(List<String> keys, OutputStream target) {
        return ZipStreamPacker.pack(storage, keys, target);
    }

    public InstantUploadStats.InstantUploadStat instantUploadStat() {
        return instantUploadStats.snapshot();
    }

    public InstantUploadStats.InstantUploadStat backendInstantUploadStat() {
        return dedupWrapper == null ? new InstantUploadStats().snapshot() : dedupWrapper.stats().snapshot();
    }

    public StorageMetricsRecorder metrics() {
        return metrics;
    }

    public MetadataStore metadataStore() {
        return metadataStore;
    }

    private InstantUploadService dedupService() {
        return dedupWrapper == null ? null : dedupWrapper.of(storage.activeBackend());
    }

    // 过渡期双写：主后端成功即返回成功，副后端失败只告警，保证切换期间写入始终可用
    private void writeSecondary(String secondaryName, java.nio.file.Path spool, UploadResult result,
                                String contentType) {
        if (secondaryName == null || spool == null || result.instant()) {
            return;
        }
        try (InputStream input = java.nio.file.Files.newInputStream(spool)) {
            registry.require(secondaryName).upload(result.key(), input, java.nio.file.Files.size(spool), contentType);
        } catch (java.io.IOException | RuntimeException ex) {
            log.error("双写副后端失败，主后端已写入成功：{}，副后端：{}，原因：{}", result.key(), secondaryName,
                    ex.getMessage());
        }
    }

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (java.io.IOException ex) {
            log.debug("关闭上传流失败：{}", ex.getMessage());
        }
    }

    private static void deleteQuietly(java.nio.file.Path path) {
        if (path == null) {
            return;
        }
        try {
            java.nio.file.Files.deleteIfExists(path);
        } catch (java.io.IOException ex) {
            path.toFile().deleteOnExit();
        }
    }

    private String secondaryBackend() {
        return storage.secondaryBackend();
    }
}