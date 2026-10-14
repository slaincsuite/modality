package cn.slainc.storage.s3;

import cn.slainc.core.constant.MimeTypes;
import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.spi.PartInfo;
import cn.slainc.storage.spi.StorageMetadata;
import cn.slainc.storage.spi.StorageObject;
import cn.slainc.storage.support.ContentTypes;
import cn.slainc.utils.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class S3FileStorage implements ExtendedFileStorage, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);

    private final String backend;

    private final S3Client client;

    private final S3Presigner presigner;

    private final String bucket;

    public S3FileStorage(String backend, S3Client client, S3Presigner presigner, String bucket) {
        this.backend = backend;
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    public S3Client client() {
        return client;
    }

    @Override
    public void close() {
        if (presigner != null) {
            presigner.close();
        }
        client.close();
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
            PutObjectRequest.Builder request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType == null ? MimeTypes.OCTET_STREAM : contentType);
            if (size >= 0) {
                client.putObject(request.build(), RequestBody.fromInputStream(data, size));
                return;
            }
            Path temp = Files.createTempFile("storage-s3-", ".tmp");
            try {
                Files.copy(data, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                client.putObject(request.build(), RequestBody.fromFile(temp));
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ex) {
            throw new StorageException("写入 S3 对象失败：" + key, ex);
        } catch (S3Exception ex) {
            throw new StorageException("写入 S3 对象失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public InputStream download(String key) {
        try {
            ResponseInputStream<?> stream = client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return stream;
        } catch (NoSuchKeyException ex) {
            throw new StorageException("文件不存在：" + key);
        } catch (S3Exception ex) {
            throw new StorageException("读取 S3 对象失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public void download(String key, OutputStream target) {
        try {
            client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(),
                    ResponseTransformer.toOutputStream(target));
        } catch (NoSuchKeyException ex) {
            throw new StorageException("文件不存在：" + key);
        } catch (S3Exception ex) {
            throw new StorageException("读取 S3 对象失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public boolean delete(String key) {
        // 三后端统一契约：对象不存在返回 false，删除由调用方决定是否记账
        if (!exists(key)) {
            return false;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception ex) {
            throw new StorageException("删除 S3 对象失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw new StorageException("检查 S3 对象是否存在失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public StorageMetadata metadata(String key) {
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return StorageMetadata.builder(key)
                    .size(response.contentLength() == null ? 0L : response.contentLength())
                    .contentType(response.contentType() == null ? ContentTypes.detect(key) : response.contentType())
                    .etag(stripQuotes(response.eTag()))
                    .md5(stripQuotes(response.eTag()))
                    .createdAt(response.lastModified())
                    .lastModified(response.lastModified())
                    .build();
        } catch (NoSuchKeyException ex) {
            throw new StorageException("文件不存在：" + key);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new StorageException("文件不存在：" + key);
            }
            throw new StorageException("读取 S3 对象元数据失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public List<StorageObject> list(String prefix) {
        List<StorageObject> objects = new ArrayList<>();
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix == null ? "" : prefix)
                    .build();
            for (ListObjectsV2Response response : client.listObjectsV2Paginator(request)) {
                for (S3Object object : response.contents()) {
                    objects.add(new StorageObject(object.key(), object.size() == null ? 0L : object.size(),
                            ContentTypes.detect(object.key()), stripQuotes(object.eTag()), object.lastModified()));
                }
            }
            return objects;
        } catch (S3Exception ex) {
            throw new StorageException("列出 S3 对象失败：" + prefix + "，" + describe(ex), ex);
        }
    }

    @Override
    public boolean supportsMultipart() {
        return true;
    }

    @Override
    public String initMultipart(String key, String contentType) {
        try {
            CreateMultipartUploadResponse response = client.createMultipartUpload(
                    CreateMultipartUploadRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType == null ? MimeTypes.OCTET_STREAM : contentType)
                            .build());
            return response.uploadId();
        } catch (S3Exception ex) {
            throw new StorageException("初始化 S3 分片上传失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public PartInfo uploadPart(String key, String uploadId, int partNumber, InputStream data, long size) {
        try {
            Path temp = null;
            RequestBody body;
            long length = size;
            if (size >= 0) {
                body = RequestBody.fromInputStream(data, size);
            } else {
                temp = Files.createTempFile("storage-s3-part-", ".tmp");
                length = Files.copy(data, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                body = RequestBody.fromFile(temp);
            }
            try {
                String etag = client.uploadPart(UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength(length)
                        .build(), body).eTag();
                return new PartInfo(partNumber, length, etag);
            } finally {
                if (temp != null) {
                    Files.deleteIfExists(temp);
                }
            }
        } catch (IOException ex) {
            throw new StorageException("上传 S3 分片失败：" + key + " 第 " + partNumber + " 片", ex);
        } catch (S3Exception ex) {
            throw new StorageException("上传 S3 分片失败：" + key + " 第 " + partNumber + " 片，" + describe(ex), ex);
        }
    }

    @Override
    public List<PartInfo> listParts(String key, String uploadId) {
        List<PartInfo> parts = new ArrayList<>();
        try {
            client.listParts(ListPartsRequest.builder().bucket(bucket).key(key).uploadId(uploadId).build())
                    .parts()
                    .forEach(part -> parts.add(new PartInfo(part.partNumber(),
                            part.size() == null ? 0L : part.size(), stripQuotes(part.eTag()))));
            parts.sort(Comparator.comparingInt(PartInfo::partNumber));
            return parts;
        } catch (S3Exception ex) {
            throw new StorageException("列出 S3 已上传分片失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public void completeMultipart(String key, String uploadId, List<PartInfo> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new StorageException("没有可合并的分片：" + key);
        }
        List<CompletedPart> completed = new ArrayList<>();
        for (PartInfo part : parts) {
            completed.add(CompletedPart.builder().partNumber(part.partNumber()).eTag(part.etag()).build());
        }
        try {
            client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completed).build())
                    .build());
        } catch (S3Exception ex) {
            throw new StorageException("合并 S3 分片失败：" + key + "，" + describe(ex), ex);
        }
    }

    @Override
    public void abortMultipart(String key, String uploadId) {
        try {
            client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .build());
        } catch (S3Exception ex) {
            log.warn("终止 S3 分片上传失败：{}，原因：{}", key, describe(ex));
        }
    }

    @Override
    public boolean supportsSignedUrl() {
        return presigner != null;
    }

    @Override
    public String presignedUploadUrl(String key, Duration expiry) {
        requirePresigner();
        PresignedPutObjectRequest request = presigner.presignPutObject(builder -> builder
                .signatureDuration(expiry)
                .putObjectRequest(put -> put.bucket(bucket).key(key)));
        return request.url().toString();
    }

    @Override
    public String presignedDownloadUrl(String key, Duration expiry) {
        requirePresigner();
        PresignedGetObjectRequest request = presigner.presignGetObject(builder -> builder
                .signatureDuration(expiry)
                .getObjectRequest(get -> get.bucket(bucket).key(key)));
        return request.url().toString();
    }

    @Override
    public boolean supportsBucket() {
        return true;
    }

    @Override
    public boolean bucketExists(String name) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(name).build());
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw new StorageException("检查 S3 桶是否存在失败：" + name + "，" + describe(ex), ex);
        }
    }

    @Override
    public void createBucket(String name) {
        try {
            client.createBucket(CreateBucketRequest.builder().bucket(name).build());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 409 || "BucketAlreadyOwnedByYou".equals(errorCode(ex))
                    || "BucketAlreadyExists".equals(errorCode(ex))) {
                return;
            }
            throw new StorageException("创建 S3 桶失败：" + name + "，" + describe(ex), ex);
        }
    }

    @Override
    public void deleteBucket(String name) {
        try {
            client.deleteBucket(DeleteBucketRequest.builder().bucket(name).build());
        } catch (S3Exception ex) {
            throw new StorageException("删除 S3 桶失败：" + name + "，" + describe(ex), ex);
        }
    }

    @Override
    public List<String> listBuckets() {
        try {
            return client.listBuckets().buckets().stream().map(Bucket::name).sorted().toList();
        } catch (S3Exception ex) {
            throw new StorageException("列出 S3 桶失败，" + describe(ex), ex);
        }
    }

    public long totalSize() {
        return list(null).stream().mapToLong(StorageObject::size).sum();
    }

    private void requirePresigner() {
        if (presigner == null) {
            throw new StorageException("当前 S3 后端未配置预签名器，无法生成预签名地址");
        }
    }

    private static String stripQuotes(String etag) {
        if (etag == null) {
            return null;
        }
        return etag.startsWith("\"") && etag.endsWith("\"") && etag.length() > 1
                ? etag.substring(1, etag.length() - 1)
                : etag;
    }

    private static String errorCode(S3Exception ex) {
        AwsErrorDetails details = ex.awsErrorDetails();
        return details == null ? null : details.errorCode();
    }

    private static String describe(S3Exception ex) {
        return "S3 错误码 " + ex.statusCode() + " / " + (errorCode(ex) == null ? "未知" : errorCode(ex));
    }
}
