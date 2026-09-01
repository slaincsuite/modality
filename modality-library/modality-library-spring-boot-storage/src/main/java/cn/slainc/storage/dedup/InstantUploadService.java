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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InstantUploadService extends DelegatingFileStorage {

    private static final Logger log = LoggerFactory.getLogger(InstantUploadService.class);

    private final Map<String, String> logicalKeys = new ConcurrentHashMap<>();

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    private final InstantUploadStats stats;

    public InstantUploadService(ExtendedFileStorage delegate) {
        this(delegate, new InstantUploadStats());
    }

    public InstantUploadService(ExtendedFileStorage delegate, InstantUploadStats stats) {
        super(delegate);
        this.stats = stats;
    }

    public InstantUploadStats stats() {
        return stats;
    }

    public int referenceCount(String md5) {
        Entry entry = entries.get(md5);
        return entry == null ? 0 : entry.references.get();
    }

    public String physicalKey(String key) {
        String md5 = logicalKeys.get(key);
        if (md5 == null) {
            return key;
        }
        Entry entry = entries.get(md5);
        return entry == null ? key : entry.key;
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
        String md5 = logicalKeys.remove(key);
        if (md5 == null) {
            return delegate().delete(key);
        }
        Entry entry = entries.get(md5);
        if (entry == null) {
            return delegate().delete(key);
        }
        int remaining = entry.references.decrementAndGet();
        if (remaining > 0) {
            return true;
        }
        entries.remove(md5, entry);
        return delegate().delete(entry.key);
    }

    @Override
    public boolean exists(String key) {
        return logicalKeys.containsKey(key) || delegate().exists(key);
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
        Entry existing = entries.get(md5);
        if (existing != null) {
            logicalKeys.put(key, md5);
            int references = existing.references.incrementAndGet();
            stats.recordHit(size);
            log.info("命中秒传，逻辑键 {} 复用物理对象 {}，当前引用计数 {}", key, existing.key, references);
            return new UploadResult(existing.key, delegate().backend(), size, md5, contentType, true, references,
                    Instant.now());
        }
        delegate().upload(key, data, size, contentType);
        Entry entry = new Entry(key, new AtomicInteger(1));
        Entry previous = entries.putIfAbsent(md5, entry);
        if (previous != null) {
            int references = previous.references.incrementAndGet();
            logicalKeys.put(key, md5);
            delegate().delete(key);
            stats.recordHit(size);
            return new UploadResult(previous.key, delegate().backend(), size, md5, contentType, true, references,
                    Instant.now());
        }
        logicalKeys.put(key, md5);
        return new UploadResult(key, delegate().backend(), size, md5, contentType, false, 1, Instant.now());
    }

    private record Entry(String key, AtomicInteger references) {
    }
}
