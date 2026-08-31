package cn.slainc.storage.support;

import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.metrics.MetricsFileStorage;
import cn.slainc.storage.metrics.StorageMetricsRecorder;

public class MetricsStorageWrapper implements StorageWrapper {

    private final StorageMetricsRecorder metrics;

    public MetricsStorageWrapper(StorageMetricsRecorder metrics) {
        this.metrics = metrics;
    }

    @Override
    public ExtendedFileStorage wrap(ExtendedFileStorage delegate) {
        return new MetricsFileStorage(delegate, metrics);
    }
}
