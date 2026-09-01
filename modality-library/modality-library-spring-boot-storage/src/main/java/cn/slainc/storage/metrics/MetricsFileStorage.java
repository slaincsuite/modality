package cn.slainc.storage.metrics;

import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.model.PartInfo;
import cn.slainc.storage.support.DelegatingFileStorage;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;

public class MetricsFileStorage extends DelegatingFileStorage {

    private final StorageMetricsRecorder metrics;

    public MetricsFileStorage(ExtendedFileStorage delegate, StorageMetricsRecorder metrics) {
        super(delegate);
        this.metrics = metrics == null ? StorageMetricsRecorder.NOOP : metrics;
    }

    @Override
    public void upload(String key, InputStream data, long size, String contentType) {
        long start = System.currentTimeMillis();
        try {
            delegate().upload(key, data, size, contentType);
            metrics.recordUpload(delegate().backend(), size, true, System.currentTimeMillis() - start);
        } catch (RuntimeException ex) {
            metrics.recordUpload(delegate().backend(), size, false, System.currentTimeMillis() - start);
            metrics.recordFailure(delegate().backend(), "upload");
            throw ex;
        }
    }

    @Override
    public InputStream download(String key) {
        long start = System.currentTimeMillis();
        try {
            InputStream input = delegate().download(key);
            metrics.recordDownload(delegate().backend(), 0L, true, System.currentTimeMillis() - start);
            return input;
        } catch (RuntimeException ex) {
            metrics.recordDownload(delegate().backend(), 0L, false, System.currentTimeMillis() - start);
            metrics.recordFailure(delegate().backend(), "download");
            throw ex;
        }
    }

    @Override
    public void download(String key, OutputStream target) {
        long start = System.currentTimeMillis();
        try {
            delegate().download(key, target);
            metrics.recordDownload(delegate().backend(), 0L, true, System.currentTimeMillis() - start);
        } catch (RuntimeException ex) {
            metrics.recordDownload(delegate().backend(), 0L, false, System.currentTimeMillis() - start);
            metrics.recordFailure(delegate().backend(), "download");
            throw ex;
        }
    }

    @Override
    public boolean delete(String key) {
        try {
            boolean deleted = delegate().delete(key);
            metrics.recordDelete(delegate().backend(), true);
            return deleted;
        } catch (RuntimeException ex) {
            metrics.recordDelete(delegate().backend(), false);
            metrics.recordFailure(delegate().backend(), "delete");
            throw ex;
        }
    }

    @Override
    public PartInfo uploadPart(String key, String uploadId, int partNumber, InputStream data, long size) {
        PartInfo part = delegate().uploadPart(key, uploadId, partNumber, data, size);
        metrics.recordMultipart(delegate().backend(), 1);
        return part;
    }

    @Override
    public String presignedDownloadUrl(String key, Duration expiry) {
        return delegate().presignedDownloadUrl(key, expiry);
    }
}
