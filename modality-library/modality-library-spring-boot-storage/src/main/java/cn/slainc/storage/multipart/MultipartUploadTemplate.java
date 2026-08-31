package cn.slainc.storage.multipart;

import cn.slainc.storage.MultipartFileStorage;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.model.PartInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MultipartUploadTemplate {

    public static final int MIN_PART_SIZE = 5 * 1024 * 1024;

    private final MultipartFileStorage storage;

    private final int partSize;

    private final ExecutorService executor;

    public MultipartUploadTemplate(MultipartFileStorage storage) {
        this(storage, MIN_PART_SIZE);
    }

    public MultipartUploadTemplate(MultipartFileStorage storage, int partSize) {
        this.storage = storage;
        this.partSize = Math.max(partSize, 1);
        this.executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "storage-multipart");
            thread.setDaemon(true);
            return thread;
        });
    }

    public MultipartSession init(String key, long totalSize, String contentType) {
        if (totalSize < 0) {
            throw new StorageException("初始化分片上传必须提供总长度：" + key);
        }
        String uploadId = storage.initMultipart(key, contentType);
        return new MultipartSession(key, uploadId, totalSize, partSize, contentType);
    }

    public PartInfo uploadPart(MultipartSession session, int partNumber, InputStream data, long size) {
        PartInfo part = storage.uploadPart(session.key(), session.uploadId(), partNumber, data, size);
        session.markUploaded(part);
        return part;
    }

    public List<PartInfo> listParts(MultipartSession session) {
        return storage.listParts(session.key(), session.uploadId());
    }

    public MultipartSession resume(MultipartSession session) {
        session.restore(listParts(session));
        return session;
    }

    public void uploadMissingParts(MultipartSession session, PartSource source, UploadProgressListener listener) {
        UploadProgressListener progress = listener == null ? UploadProgressListener.NOOP : listener;
        for (Integer partNumber : session.pendingPartNumbers()) {
            long length = session.partLength(partNumber);
            try (InputStream input = source.open(partNumber, session.partOffset(partNumber), length)) {
                uploadPart(session, partNumber, input, length);
            } catch (IOException ex) {
                throw new StorageException("读取分片数据源失败：" + session.key() + " 第 " + partNumber + " 片", ex);
            }
            progress.onProgress(progress(session, partNumber));
        }
    }

    public void uploadConcurrently(MultipartSession session, PartSource source, int concurrency,
                                   UploadProgressListener listener) {
        UploadProgressListener progress = listener == null ? UploadProgressListener.NOOP : listener;
        List<Integer> pending = session.pendingPartNumbers();
        if (pending.isEmpty()) {
            return;
        }
        int limit = Math.max(1, Math.min(concurrency, pending.size()));
        ExecutorService pool = Executors.newFixedThreadPool(limit, runnable -> {
            Thread thread = new Thread(runnable, "storage-multipart-pool");
            thread.setDaemon(true);
            return thread;
        });
        Object lock = new Object();
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Integer partNumber : pending) {
                futures.add(CompletableFuture.runAsync(() -> {
                    long length = session.partLength(partNumber);
                    try (InputStream input = source.open(partNumber, session.partOffset(partNumber), length)) {
                        uploadPart(session, partNumber, input, length);
                    } catch (IOException ex) {
                        throw new CompletionException(
                                new StorageException("读取分片数据源失败：" + session.key() + " 第 " + partNumber + " 片", ex));
                    }
                    synchronized (lock) {
                        progress.onProgress(progress(session, partNumber));
                    }
                }, pool));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof StorageException storageException) {
                throw storageException;
            }
            throw new StorageException("并发上传分片失败：" + session.key(), cause);
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void complete(MultipartSession session) {
        if (session.uploadedPartCount() < session.totalParts()) {
            throw new StorageException("分片未上传完整，当前 " + session.uploadedPartCount() + " 片，共需要 "
                    + session.totalParts() + " 片，请先执行断点续传");
        }
        storage.completeMultipart(session.key(), session.uploadId(), session.uploadedParts());
        session.markCompleted();
    }

    public void abort(MultipartSession session) {
        storage.abortMultipart(session.key(), session.uploadId());
    }

    public boolean supportsMultipart() {
        return storage.supportsMultipart();
    }

    public void close() {
        executor.shutdown();
    }

    private UploadProgress progress(MultipartSession session, int partNumber) {
        return new UploadProgress(session.key(), partNumber, session.uploadedPartCount(), session.totalParts(),
                session.uploadedBytes(), session.totalSize());
    }
}
