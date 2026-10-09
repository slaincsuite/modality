package cn.slainc.storage.routing;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.model.PartInfo;
import cn.slainc.storage.model.StorageMetadata;
import cn.slainc.storage.model.StorageObject;
import cn.slainc.storage.support.StorageConfigKeys;
import cn.slainc.storage.support.StorageWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingFileStorage implements ExtendedFileStorage {

    private static final Logger log = LoggerFactory.getLogger(RoutingFileStorage.class);

    private final StorageBackendRegistry registry;

    private final DynamicConfigService configService;

    private final String defaultBackend;

    private final List<StorageWrapper> wrappers;

    private final java.util.Set<String> history = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    private final Map<String, ExtendedFileStorage> decorated = new ConcurrentHashMap<>();

    private volatile String activeName;

    private volatile ExtendedFileStorage active;

    public RoutingFileStorage(StorageBackendRegistry registry, DynamicConfigService configService,
                              String defaultBackend, List<StorageWrapper> wrappers) {
        this.registry = registry;
        this.configService = configService;
        this.defaultBackend = defaultBackend;
        this.wrappers = wrappers == null ? List.of() : List.copyOf(wrappers);
        this.activeName = defaultBackend;
        this.history.add(defaultBackend);
    }

    @Override
    public String backend() {
        return activeBackend();
    }

    public String activeBackend() {
        String configured = configService == null
                ? defaultBackend
                : configService.getString(StorageConfigKeys.BACKEND, defaultBackend);
        if (configured == null || configured.isBlank()) {
            configured = defaultBackend;
        }
        if (!configured.equals(activeName)) {
            switchTo(configured);
        }
        return activeName;
    }

    public ExtendedFileStorage current() {
        activeBackend();
        ExtendedFileStorage current = active;
        if (current == null) {
            synchronized (this) {
                if (active == null) {
                    active = decorated(activeName);
                }
                current = active;
            }
        }
        return current;
    }

    public String secondaryBackend() {
        if (configService == null
                || !configService.getBoolean(StorageConfigKeys.DUAL_WRITE_ENABLED, false)) {
            return null;
        }
        String name = configService.getString(StorageConfigKeys.DUAL_WRITE_BACKEND, null);
        if (name == null || name.isBlank() || name.equals(activeBackend())) {
            return null;
        }
        return registry.contains(name) ? name : null;
    }

    public StorageBackendRegistry registry() {
        return registry;
    }

    public String defaultBackend() {
        return defaultBackend;
    }

    public synchronized void switchTo(String name) {
        if (name != null && name.equals(activeName) && active != null) {
            return;
        }
        ExtendedFileStorage target = registry.find(name).orElse(null);
        if (target == null) {
            log.error("配置的对象存储后端不存在：{}，继续使用既有后端：{}，可用后端：{}", name, activeName, registry.names());
            return;
        }
        String previous = activeName;
        this.active = decorated(target.backend());
        this.activeName = target.backend();
        this.history.add(activeName);
        log.info("对象存储后端已切换：{} -> {}", previous, activeName);
    }

    // 读写必须共用同一装饰实例，否则加密、秒传、杀毒、指标只在写侧生效
    private ExtendedFileStorage decorated(String name) {
        return decorated.computeIfAbsent(name, key -> decorate(registry.require(key)));
    }

    private ExtendedFileStorage candidate(String name) {
        return registry.contains(name) ? decorated(name) : null;
    }

    private ExtendedFileStorage decorate(ExtendedFileStorage storage) {
        ExtendedFileStorage result = storage;
        for (StorageWrapper wrapper : wrappers) {
            result = wrapper.wrap(result);
        }
        return result;
    }

    @Override
    public void upload(String key, InputStream data, long size, String contentType) {
        current().upload(key, data, size, contentType);
    }

    // 热切换后旧对象仍留在原后端，读取按「当前后端 -> 历史后端」顺序回退，避免切换瞬间历史文件不可读
    public List<String> readCandidateNames() {
        List<String> names = new java.util.ArrayList<>();
        names.add(activeBackend());
        for (String name : history) {
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    @Override
    public InputStream download(String key) {
        RuntimeException last = null;
        for (String name : readCandidateNames()) {
            ExtendedFileStorage candidate = candidate(name);
            if (candidate == null) {
                continue;
            }
            try {
                return candidate.download(key);
            } catch (RuntimeException ex) {
                last = ex;
            }
        }
        throw last == null ? new StorageException("文件不存在：" + key) : last;
    }

    @Override
    public void download(String key, OutputStream target) {
        try (InputStream input = download(key)) {
            input.transferTo(target);
        } catch (java.io.IOException ex) {
            throw new StorageException("写出对象内容失败：" + key, ex);
        }
    }

    @Override
    public boolean delete(String key) {
        boolean deleted = false;
        for (String name : readCandidateNames()) {
            ExtendedFileStorage candidate = candidate(name);
            if (candidate == null || !candidate.exists(key)) {
                continue;
            }
            deleted = candidate.delete(key) || deleted;
        }
        return deleted;
    }

    @Override
    public boolean exists(String key) {
        for (String name : readCandidateNames()) {
            ExtendedFileStorage candidate = candidate(name);
            if (candidate != null && candidate.exists(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public StorageMetadata metadata(String key) {
        RuntimeException last = null;
        for (String name : readCandidateNames()) {
            ExtendedFileStorage candidate = candidate(name);
            if (candidate == null) {
                continue;
            }
            try {
                return candidate.metadata(key);
            } catch (RuntimeException ex) {
                last = ex;
            }
        }
        throw last == null ? new StorageException("文件不存在：" + key) : last;
    }

    @Override
    public List<StorageObject> list(String prefix) {
        return current().list(prefix);
    }

    @Override
    public boolean supportsMultipart() {
        return current().supportsMultipart();
    }

    @Override
    public String initMultipart(String key, String contentType) {
        return current().initMultipart(key, contentType);
    }

    @Override
    public PartInfo uploadPart(String key, String uploadId, int partNumber, InputStream data, long size) {
        return current().uploadPart(key, uploadId, partNumber, data, size);
    }

    @Override
    public List<PartInfo> listParts(String key, String uploadId) {
        return current().listParts(key, uploadId);
    }

    @Override
    public void completeMultipart(String key, String uploadId, List<PartInfo> parts) {
        current().completeMultipart(key, uploadId, parts);
    }

    @Override
    public void abortMultipart(String key, String uploadId) {
        current().abortMultipart(key, uploadId);
    }

    @Override
    public boolean supportsSignedUrl() {
        return current().supportsSignedUrl();
    }

    @Override
    public String presignedUploadUrl(String key, Duration expiry) {
        return current().presignedUploadUrl(key, expiry);
    }

    @Override
    public String presignedDownloadUrl(String key, Duration expiry) {
        return current().presignedDownloadUrl(key, expiry);
    }

    @Override
    public boolean supportsBucket() {
        return current().supportsBucket();
    }

    @Override
    public boolean bucketExists(String bucket) {
        return current().bucketExists(bucket);
    }

    @Override
    public void createBucket(String bucket) {
        current().createBucket(bucket);
    }

    @Override
    public void deleteBucket(String bucket) {
        current().deleteBucket(bucket);
    }

    @Override
    public List<String> listBuckets() {
        return current().listBuckets();
    }
}
