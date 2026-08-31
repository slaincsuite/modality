package cn.slainc.storage.minio;

import cn.slainc.core.constant.MimeTypes;
import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.model.PartInfo;
import cn.slainc.storage.model.StorageMetadata;
import cn.slainc.storage.model.StorageObject;
import cn.slainc.storage.support.ContentTypes;
import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MinioFileStorage implements ExtendedFileStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioFileStorage.class);

    private static final String PART_ROOT = "__parts/";

    private static final long UNKNOWN_SIZE_PART_SIZE = 10L * 1024 * 1024;

    private final String backend;

    private final MinioClient client;

    private final String bucket;

    public MinioFileStorage(String backend, MinioClient client, String bucket) {
        this.backend = backend;
        this.client = client;
        this.bucket = bucket;
    }

    public MinioClient client() {
        return client;
    }

    public String bucket() {
        return bucket;
    }

    @Override
    public String backend() {
        return backend;
    }

    @Override
    public void upload(String key, InputStream data, long size, String contentType) {
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .contentType(contentType == null ? MimeTypes.OCTET_STREAM : contentType);
            if (size >= 0) {
                client.putObject(builder.stream(data, size, -1).build());
            } else {
                client.putObject(builder.stream(data, -1, UNKNOWN_SIZE_PART_SIZE).build());
            }
        } catch (Exception ex) {
            throw new StorageException("写入 MinIO 对象失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public InputStream download(String key) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (ErrorResponseException ex) {
            if (isMissing(ex)) {
                throw new StorageException("文件不存在：" + key);
            }
            throw new StorageException("读取 MinIO 对象失败：" + key + "，" + describe(ex), ex);
        } catch (Exception ex) {
            throw new StorageException("读取 MinIO 对象失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public void download(String key, OutputStream target) {
        try (InputStream input = download(key)) {
            input.transferTo(target);
        } catch (IOException ex) {
            throw new StorageException("写出 MinIO 对象失败：" + key, ex);
        }
    }

    @Override
    public boolean delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (Exception ex) {
            throw new StorageException("删除 MinIO 对象失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (ErrorResponseException ex) {
            if (isMissing(ex)) {
                return false;
            }
            throw new StorageException("检查 MinIO 对象是否存在失败：" + key + "，" + describe(ex), ex);
        } catch (Exception ex) {
            throw new StorageException("检查 MinIO 对象是否存在失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public StorageMetadata metadata(String key) {
        try {
            StatObjectResponse response = client.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            return StorageMetadata.builder(key)
                    .size(response.size())
                    .contentType(response.contentType() == null ? ContentTypes.detect(key) : response.contentType())
                    .etag(response.etag())
                    .md5(response.etag())
                    .createdAt(toInstant(response.lastModified()))
                    .lastModified(toInstant(response.lastModified()))
                    .build();
        } catch (ErrorResponseException ex) {
            if (isMissing(ex)) {
                throw new StorageException("文件不存在：" + key);
            }
            throw new StorageException("读取 MinIO 对象元数据失败：" + key + "，" + describe(ex), ex);
        } catch (Exception ex) {
            throw new StorageException("读取 MinIO 对象元数据失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public List<StorageObject> list(String prefix) {
        List<StorageObject> objects = new ArrayList<>();
        try {
            for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix == null ? "" : prefix)
                    .recursive(true)
                    .build())) {
                Item item = result.get();
                if (item.objectName().startsWith(PART_ROOT)) {
                    continue;
                }
                objects.add(new StorageObject(item.objectName(), item.size(), ContentTypes.detect(item.objectName()),
                        item.etag(), toInstant(item.lastModified())));
            }
            objects.sort(Comparator.comparing(StorageObject::key));
            return objects;
        } catch (Exception ex) {
            throw new StorageException("列出 MinIO 对象失败：" + prefix + "，" + describe(ex), ex);
        }
    }

    @Override
    public boolean supportsMultipart() {
        return true;
    }

    @Override
    public String initMultipart(String key, String contentType) {
        String uploadId = java.util.UUID.randomUUID().toString().replace("-", "");
        try {
            // MinIO SDK 8.x 未暴露 S3 分片级 API，这里用服务端 compose 实现等价能力
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(partKey(uploadId, 0))
                    .stream(InputStream.nullInputStream(), 0, -1)
                    .contentType("application/x-modality-multipart")
                    .build());
        } catch (Exception ex) {
            throw new StorageException("初始化 MinIO 分片上传失败：" + key + "，" + describe(ex), ex);
        }
        return uploadId;
    }

    @Override
    public PartInfo uploadPart(String key, String uploadId, int partNumber, InputStream data, long size) {
        if (partNumber < 1) {
            throw new StorageException("分片序号必须从 1 开始，当前为：" + partNumber);
        }
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(partKey(uploadId, partNumber));
            if (size >= 0) {
                client.putObject(builder.stream(data, size, -1).build());
            } else {
                size = data.transferTo(OutputStream.nullOutputStream());
                client.putObject(builder.stream(data, -1, UNKNOWN_SIZE_PART_SIZE).build());
            }
            return new PartInfo(partNumber, size);
        } catch (IOException ex) {
            throw new StorageException("上传 MinIO 分片失败：" + key + " 第 " + partNumber + " 片", ex);
        } catch (Exception ex) {
            throw new StorageException("上传 MinIO 分片失败：" + key + " 第 " + partNumber + " 片，" + describe(ex), ex);
        }
    }

    @Override
    public List<PartInfo> listParts(String key, String uploadId) {
        List<PartInfo> parts = new ArrayList<>();
        try {
            for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(partPrefix(uploadId))
                    .recursive(true)
                    .build())) {
                Item item = result.get();
                int number = parsePartNumber(item.objectName());
                if (number <= 0) {
                    continue;
                }
                parts.add(new PartInfo(number, item.size(), item.etag()));
            }
            parts.sort(Comparator.comparingInt(PartInfo::partNumber));
            return parts;
        } catch (Exception ex) {
            throw new StorageException("列出 MinIO 已上传分片失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public void completeMultipart(String key, String uploadId, List<PartInfo> parts) {
        List<PartInfo> ordered = new ArrayList<>(parts);
        ordered.sort(Comparator.comparingInt(PartInfo::partNumber));
        if (ordered.isEmpty()) {
            throw new StorageException("没有可合并的分片：" + key);
        }
        List<ComposeSource> sources = ordered.stream()
                .map(part -> ComposeSource.builder().bucket(bucket).object(partKey(uploadId, part.partNumber()))
                        .build())
                .toList();
        try {
            client.composeObject(ComposeObjectArgs.builder().bucket(bucket).object(key).sources(sources).build());
        } catch (Exception ex) {
            log.warn("MinIO 服务端 compose 合并失败，降级为客户端串流合并：{}，原因：{}", key, describe(ex));
            mergeOnClient(key, uploadId, ordered);
        } finally {
            cleanupParts(uploadId, ordered.size());
        }
    }

    @Override
    public void abortMultipart(String key, String uploadId) {
        cleanupParts(uploadId, Integer.MAX_VALUE);
    }

    @Override
    public boolean supportsSignedUrl() {
        return true;
    }

    @Override
    public String presignedUploadUrl(String key, Duration expiry) {
        return presigned(key, Method.PUT, expiry);
    }

    @Override
    public String presignedDownloadUrl(String key, Duration expiry) {
        return presigned(key, Method.GET, expiry);
    }

    @Override
    public boolean supportsBucket() {
        return true;
    }

    @Override
    public boolean bucketExists(String name) {
        try {
            return client.bucketExists(BucketExistsArgs.builder().bucket(name).build());
        } catch (Exception ex) {
            throw new StorageException("检查 MinIO 桶是否存在失败：" + name + "，" + describe(ex), ex);
        }
    }

    @Override
    public void createBucket(String name) {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(name).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(name).build());
            }
        } catch (Exception ex) {
            throw new StorageException("创建 MinIO 桶失败：" + name + "，" + describe(ex), ex);
        }
    }

    @Override
    public void deleteBucket(String name) {
        try {
            client.removeBucket(RemoveBucketArgs.builder().bucket(name).build());
        } catch (Exception ex) {
            throw new StorageException("删除 MinIO 桶失败：" + name + "，" + describe(ex), ex);
        }
    }

    @Override
    public List<String> listBuckets() {
        List<String> buckets = new ArrayList<>();
        try {
            client.listBuckets().forEach(bucket -> buckets.add(bucket.name()));
            buckets.sort(Comparator.naturalOrder());
            return buckets;
        } catch (Exception ex) {
            throw new StorageException("列出 MinIO 桶失败，" + describe(ex), ex);
        }
    }

    public long totalSize() {
        return list(null).stream().mapToLong(StorageObject::size).sum();
    }

    private String presigned(String key, Method method, Duration expiry) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(bucket)
                    .object(key)
                    .expiry((int) Math.max(1, expiry.toSeconds()), TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            throw new StorageException("生成 MinIO 预签名地址失败：" + key + "，" + describe(ex), ex);
        }
    }

    private void mergeOnClient(String key, String uploadId, List<PartInfo> parts) {
        Path temp = null;
        try {
            temp = Files.createTempFile("storage-minio-merge-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temp)) {
                for (PartInfo part : parts) {
                    try (InputStream input = client.getObject(GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(partKey(uploadId, part.partNumber()))
                            .build())) {
                        input.transferTo(output);
                    }
                }
            }
            try (InputStream input = Files.newInputStream(temp)) {
                client.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .stream(input, Files.size(temp), -1)
                        .build());
            }
        } catch (Exception ex) {
            throw new StorageException("客户端串流合并 MinIO 分片失败：" + key + "，" + describe(ex), ex);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ex) {
                    log.warn("清理 MinIO 合并临时文件失败：{}", temp, ex);
                }
            }
        }
    }

    private void cleanupParts(String uploadId, int count) {
        try {
            for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(partPrefix(uploadId))
                    .recursive(true)
                    .build())) {
                String object = result.get().objectName();
                client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(object).build());
            }
        } catch (Exception ex) {
            log.warn("清理 MinIO 分片临时对象失败：{}，原因：{}", uploadId, describe(ex));
        }
    }

    private static String partPrefix(String uploadId) {
        return PART_ROOT + uploadId + "/";
    }

    private static String partKey(String uploadId, int partNumber) {
        return partPrefix(uploadId) + String.format(Locale.ROOT, "%06d", partNumber);
    }

    private static int parsePartNumber(String objectName) {
        int index = objectName.lastIndexOf('/');
        if (index < 0 || index == objectName.length() - 1) {
            return -1;
        }
        try {
            return Integer.parseInt(objectName.substring(index + 1));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static boolean isMissing(ErrorResponseException ex) {
        String code = ex.errorResponse() == null ? null : ex.errorResponse().code();
        return "NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NoSuchBucket".equals(code);
    }

    private static Instant toInstant(ZonedDateTime time) {
        return time == null ? Instant.now() : time.toInstant();
    }

    private static String describe(Exception ex) {
        if (ex instanceof ErrorResponseException response) {
            return "MinIO 错误码 " + (response.errorResponse() == null ? "未知" : response.errorResponse().code());
        }
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
