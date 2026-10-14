package cn.slainc.storage.migration;

import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.spi.StorageMetadata;
import cn.slainc.storage.spi.StorageObject;
import cn.slainc.storage.routing.StorageBackendRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StorageMigrator {

    private static final Logger log = LoggerFactory.getLogger(StorageMigrator.class);

    private final StorageBackendRegistry registry;

    public StorageMigrator(StorageBackendRegistry registry) {
        this.registry = registry;
    }

    public MigrationReport migrate(String from, String to, String prefix, boolean deleteSource,
                                   Consumer<StorageObject> listener) {
        ExtendedFileStorage source = registry.require(from);
        ExtendedFileStorage target = registry.require(to);
        List<StorageObject> objects = source.list(prefix);
        int succeeded = 0;
        int skipped = 0;
        int failed = 0;
        long bytes = 0;
        List<String> failures = new ArrayList<>();
        for (StorageObject object : objects) {
            try {
                if (target.exists(object.key())) {
                    skipped++;
                } else {
                    StorageMetadata metadata = source.metadata(object.key());
                    try (var input = source.download(object.key())) {
                        target.upload(object.key(), input, metadata.size(), metadata.contentType());
                    }
                    succeeded++;
                    bytes += object.size();
                }
                if (deleteSource) {
                    source.delete(object.key());
                }
                if (listener != null) {
                    listener.accept(object);
                }
            } catch (IOException | RuntimeException ex) {
                failed++;
                failures.add(object.key() + "：" + ex.getMessage());
                log.warn("迁移对象失败：{}，从 {} 到 {}，原因：{}", object.key(), from, to, ex.getMessage());
            }
        }
        log.info("对象存储迁移完成：{} -> {}，总计 {}，成功 {}，跳过 {}，失败 {}，字节 {}", from, to, objects.size(),
                succeeded, skipped, failed, bytes);
        return new MigrationReport(from, to, objects.size(), succeeded, skipped, failed, bytes, failures);
    }

    public void requireBackend(String name) {
        if (!registry.contains(name)) {
            throw new StorageException("迁移目标后端不存在：" + name + "，可用后端：" + registry.names());
        }
    }
}
