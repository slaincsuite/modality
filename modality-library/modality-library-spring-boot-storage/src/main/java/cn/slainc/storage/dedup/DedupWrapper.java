package cn.slainc.storage.dedup;

import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.support.StorageWrapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DedupWrapper implements StorageWrapper {

    private final Map<String, InstantUploadService> services = new ConcurrentHashMap<>();

    private final InstantUploadStats stats = new InstantUploadStats();

    @Override
    public ExtendedFileStorage wrap(ExtendedFileStorage delegate) {
        return services.computeIfAbsent(delegate.backend(), name -> new InstantUploadService(delegate, stats));
    }

    public InstantUploadService of(String backend) {
        return services.get(backend);
    }

    public InstantUploadStats stats() {
        return stats;
    }

    public Map<String, InstantUploadService> services() {
        return Map.copyOf(services);
    }
}
