package cn.slainc.storage.routing;

import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.support.DelegatingFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

public class StorageBackendRegistrar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(StorageBackendRegistrar.class);

    private final StorageBackendRegistry registry;

    private final ObjectProvider<ExtendedFileStorage> storages;

    public StorageBackendRegistrar(StorageBackendRegistry registry, ObjectProvider<ExtendedFileStorage> storages) {
        this.registry = registry;
        this.storages = storages;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (ExtendedFileStorage storage : storages) {
            if (storage instanceof RoutingFileStorage || storage instanceof DelegatingFileStorage) {
                continue;
            }
            registry.register(storage);
        }
        log.info("对象存储后端注册完成，可用后端：{}", registry.names());
    }
}
