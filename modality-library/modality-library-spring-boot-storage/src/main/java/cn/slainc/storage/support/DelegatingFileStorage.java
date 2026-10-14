package cn.slainc.storage.support;

import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.spi.PartInfo;
import cn.slainc.storage.spi.StorageMetadata;
import cn.slainc.storage.spi.StorageObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;

public abstract class DelegatingFileStorage implements ExtendedFileStorage {

    private final ExtendedFileStorage delegate;

    protected DelegatingFileStorage(ExtendedFileStorage delegate) {
        this.delegate = delegate;
    }

    protected ExtendedFileStorage delegate() {
        return delegate;
    }

    @Override
    public String backend() {
        return delegate.backend();
    }

    @Override
    public void upload(String key, InputStream data, long size, String contentType) {
        delegate.upload(key, data, size, contentType);
    }

    @Override
    public InputStream download(String key) {
        return delegate.download(key);
    }

    @Override
    public void download(String key, OutputStream target) {
        delegate.download(key, target);
    }

    @Override
    public boolean delete(String key) {
        return delegate.delete(key);
    }

    @Override
    public boolean exists(String key) {
        return delegate.exists(key);
    }

    @Override
    public StorageMetadata metadata(String key) {
        return delegate.metadata(key);
    }

    @Override
    public List<StorageObject> list(String prefix) {
        return delegate.list(prefix);
    }

    @Override
    public boolean supportsMultipart() {
        return delegate.supportsMultipart();
    }

    @Override
    public String initMultipart(String key, String contentType) {
        return delegate.initMultipart(key, contentType);
    }

    @Override
    public PartInfo uploadPart(String key, String uploadId, int partNumber, InputStream data, long size) {
        return delegate.uploadPart(key, uploadId, partNumber, data, size);
    }

    @Override
    public List<PartInfo> listParts(String key, String uploadId) {
        return delegate.listParts(key, uploadId);
    }

    @Override
    public void completeMultipart(String key, String uploadId, List<PartInfo> parts) {
        delegate.completeMultipart(key, uploadId, parts);
    }

    @Override
    public void abortMultipart(String key, String uploadId) {
        delegate.abortMultipart(key, uploadId);
    }

    @Override
    public boolean supportsSignedUrl() {
        return delegate.supportsSignedUrl();
    }

    @Override
    public String presignedUploadUrl(String key, Duration expiry) {
        return delegate.presignedUploadUrl(key, expiry);
    }

    @Override
    public String presignedDownloadUrl(String key, Duration expiry) {
        return delegate.presignedDownloadUrl(key, expiry);
    }

    @Override
    public boolean supportsBucket() {
        return delegate.supportsBucket();
    }

    @Override
    public boolean bucketExists(String bucket) {
        return delegate.bucketExists(bucket);
    }

    @Override
    public void createBucket(String bucket) {
        delegate.createBucket(bucket);
    }

    @Override
    public void deleteBucket(String bucket) {
        delegate.deleteBucket(bucket);
    }

    @Override
    public List<String> listBuckets() {
        return delegate.listBuckets();
    }
}
