package cn.slainc.storage.dedup;

import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.model.StorageMetadata;
import cn.slainc.storage.model.StorageObject;
import cn.slainc.storage.model.UploadResult;
import cn.slainc.storage.support.DelegatingFileStorage;
import cn.slainc.utils.digest.DigestUtils;
import cn.slainc.utils.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class InstantUploadService extends DelegatingFileStorage {

    private static final Logger log = LoggerFactory.getLogger(InstantUploadService.class);

    private final DedupRegistry registry;

    private final InstantUploadStats stats;

    public InstantUploadService(ExtendedFileStorage delegate) {
        this(delegate, new InstantUploadStats());
    }

    public InstantUploadService(ExtendedFileStorage delegate, InstantUploadStats stats) {
        this(delegate, stats, new MemoryDedupRegistry());
    }

    public InstantUploadService(ExtendedFileStorage delegate, InstantUploadStats stats, DedupRegistry registry) {
        super(delegate);
        this.stats = stats;
        this.registry = registry == null ? new MemoryDedupRegistry() : registry;
    }

    public InstantUploadStats stats() {
        return stats;
    }

    public int referenceCount(String md5) {
        DedupRegistry.Entry entry = registry.find(digestId(md5));
        return entry == null ? 0 : entry.references();
    }

    public String physicalKey(String key) {
        String id = registry.lookup(logicalId(key));
        if (id == null) {
            return key;
        }
        DedupRegistry.Entry entry = registry.find(id);
        return entry == null ? key : entry.key();
    }

    public UploadResult upload(String key, InputStream data, long size, String contentType, String md5) {
        stats.recordAttempt();
        if (md5 == null) {
            Path temp = null;
            try {
                temp = Files.createTempFile("storage-dedup-", ".tmp");
                long actual = Files.copy(data, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                md5 = FileUtils.md5(temp);
                size = actual;
                try (InputStream input = Files.newInputStream(temp)) {
                    return doUpload(key, input, size, contentType, md5);
                }
            } catch (IOException ex) {
                throw new StorageException("计算上传内容摘要失败：" + key, ex);
            } finally {
                if (temp != null) {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException ex) {
                        log.warn("清理秒传摘要临时文件失败：{}", temp, ex);
                    }
                }
            }
        }
        return doUpload(key, data, size, contentType, md5);
    }

    @Override
    public void upload(String key, InputStream data, long size, String contentType) {
        upload(key, data, size, contentType, null);
    }

    public UploadResult upload(String key, byte[] data, String contentType) {
        String md5 = DigestUtils.md5(data);
        return doUpload(key, new java.io.ByteArrayInputStream(data), data.length, contentType, md5);
    }

    @Override
    public boolean delete(String key) {
        String id = registry.unbind(logicalId(key));
        if (id == null) {
            return delegate().delete(key);
        }
        DedupRegistry.Entry released = registry.release(id);
        if (released == null) {
            return delegate().delete(key);
        }
        if (released.references() > 0) {
            return true;
        }
        return delegate().delete(released.key());
    }

    @Override
    public boolean exists(String key) {
        return registry.lookup(logicalId(key)) != null || delegate().exists(key);
    }

    @Override
    public InputStream download(String key) {
        return delegate().download(physicalKey(key));
    }

    @Override
    public void download(String key, OutputStream target) {
        delegate().download(physicalKey(key), target);
    }

    @Override
    public StorageMetadata metadata(String key) {
        StorageMetadata physical = delegate().metadata(physicalKey(key));
        if (physical.key().equals(key)) {
            return physical;
        }
        return StorageMetadata.builder(key)
                .size(physical.size())
                .contentType(physical.contentType())
                .md5(physical.md5())
                .etag(physical.etag())
                .createdAt(physical.createdAt())
                .lastModified(physical.lastModified())
                .custom(physical.custom())
                .custom("physicalKey", physical.key())
                .build();
    }

    @Override
    public List<StorageObject> list(String prefix) {
        return delegate().list(prefix);
    }

    @Override
    public String presignedDownloadUrl(String key, Duration expiry) {
        return delegate().presignedDownloadUrl(physicalKey(key), expiry);
    }

    @Override
    public String presignedUploadUrl(String key, Duration expiry) {
        return delegate().presignedUploadUrl(physicalKey(key), expiry);
    }

    private UploadResult doUpload(String key, InputStream data, long size, String contentType, String md5) {
        String id = digestId(md5);
        DedupRegistry.Entry existing = registry.find(id);
        if (existing != null && !delegate().exists(existing.key())) {
            registry.remove(id);
            existing = null;
        }
        if (existing != null) {
            return reuse(key, size, contentType, md5, existing);
        }
        delegate().upload(key, data, size, contentType);
        DedupRegistry.Entry registered = registry.register(id, key);
        if (!key.equals(registered.key())) {
            delegate().delete(key);
            return reuse(key, size, contentType, md5, registered);
        }
        registry.bind(logicalId(key), id);
        return new UploadResult(key, delegate().backend(), size, md5, contentType, false, 1, Instant.now());
    }

    private UploadResult reuse(String key, long size, String contentType, String md5, DedupRegistry.Entry entry) {
        String id = digestId(md5);
        registry.bind(logicalId(key), id);
        DedupRegistry.Entry retained = registry.retain(id);
        int references = retained == null ? entry.references() : retained.references();
        stats.recordHit(size);
        log.info("命中秒传，逻辑键 {} 复用物理对象 {}，当前引用计数 {}", key, entry.key(), references);
        return new UploadResult(entry.key(), delegate().backend(), size, md5, contentType, true, references,
                Instant.now());
    }

    // 引用表按后端命名空间隔离，同一内容在不同后端不能互相复用物理对象
    private String digestId(String md5) {
        return delegate().backend() + ":" + md5;
    }

    private String logicalId(String key) {
        return delegate().backend() + ":" + key;
    }
}
