package cn.slainc.storage.lifecycle;

import cn.slainc.storage.FileStorage;
import cn.slainc.storage.spi.StorageMetadata;
import cn.slainc.storage.spi.StorageObject;
import cn.slainc.storage.routing.StorageBackendRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class StorageLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(StorageLifecycleService.class);

    private final FileStorage storage;

    private final StorageBackendRegistry registry;

    private final List<LifecycleRule> rules;

    public StorageLifecycleService(FileStorage storage, StorageBackendRegistry registry, List<LifecycleRule> rules) {
        this.storage = storage;
        this.registry = registry;
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public List<LifecycleRule> rules() {
        return rules;
    }

    public LifecycleReport run() {
        int scanned = 0;
        int expired = 0;
        int archived = 0;
        int failed = 0;
        long reclaimed = 0;
        List<String> failures = new ArrayList<>();
        for (LifecycleRule rule : rules) {
            List<StorageObject> objects;
            try {
                objects = storage.list(rule.prefix());
            } catch (RuntimeException ex) {
                log.warn("生命周期规则 {} 扫描失败：{}", rule.name(), ex.getMessage());
                failures.add(rule.name() + "：" + ex.getMessage());
                failed++;
                continue;
            }
            for (StorageObject object : objects) {
                scanned++;
                Instant lastModified = object.lastModified();
                if (lastModified == null) {
                    continue;
                }
                Duration age = Duration.between(lastModified, Instant.now());
                try {
                    if (rule.archiveAfter() != null && rule.archiveBackend() != null
                            && age.compareTo(rule.archiveAfter()) >= 0) {
                        archive(rule, object);
                        archived++;
                        continue;
                    }
                    if (rule.expireAfter() != null && age.compareTo(rule.expireAfter()) >= 0) {
                        if (storage.delete(object.key())) {
                            expired++;
                            reclaimed += object.size();
                        }
                    }
                } catch (RuntimeException ex) {
                    failed++;
                    failures.add(object.key() + "：" + ex.getMessage());
                    log.warn("生命周期处理对象失败：{}，规则：{}，原因：{}", object.key(), rule.name(), ex.getMessage());
                }
            }
        }
        return new LifecycleReport(scanned, expired, archived, failed, reclaimed, failures);
    }

    private void archive(LifecycleRule rule, StorageObject object) {
        FileStorage target = registry.require(rule.archiveBackend());
        if (target.exists(object.key())) {
            return;
        }
        StorageMetadata metadata = storage.metadata(object.key());
        try (var input = storage.download(object.key())) {
            target.upload(object.key(), input, metadata.size(), metadata.contentType());
        } catch (java.io.IOException ex) {
            throw new cn.slainc.storage.StorageException("归档对象失败：" + object.key(), ex);
        }
        storage.delete(object.key());
        log.info("对象 {} 已归档至冷存储后端 {}", object.key(), rule.archiveBackend());
    }
}
