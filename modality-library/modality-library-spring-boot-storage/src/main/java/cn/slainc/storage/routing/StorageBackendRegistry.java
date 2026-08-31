package cn.slainc.storage.routing;

import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.StorageException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StorageBackendRegistry {

    private final Map<String, ExtendedFileStorage> backends = new ConcurrentHashMap<>();

    public void register(ExtendedFileStorage storage) {
        if (storage == null || storage.backend() == null || storage.backend().isBlank()) {
            throw new StorageException("注册对象存储后端失败：后端标识不能为空");
        }
        backends.put(storage.backend(), storage);
    }

    public Optional<ExtendedFileStorage> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(backends.get(name));
    }

    public ExtendedFileStorage require(String name) {
        return find(name).orElseThrow(() -> new StorageException(
                "对象存储后端不存在：" + name + "，可用后端：" + names()));
    }

    public boolean contains(String name) {
        return name != null && backends.containsKey(name);
    }

    public Set<String> names() {
        return backends.keySet();
    }

    public Map<String, ExtendedFileStorage> all() {
        return new LinkedHashMap<>(backends);
    }

    public void clear() {
        backends.clear();
    }
}
