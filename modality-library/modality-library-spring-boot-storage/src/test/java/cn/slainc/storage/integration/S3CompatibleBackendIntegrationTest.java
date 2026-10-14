package cn.slainc.storage.integration;

import cn.slainc.storage.autoconfigure.StorageProperties;
import cn.slainc.storage.minio.MinioFileStorage;
import cn.slainc.storage.minio.MinioStorageFactory;
import cn.slainc.storage.spi.PartInfo;
import cn.slainc.storage.spi.StorageMetadata;
import cn.slainc.storage.spi.StorageObject;
import cn.slainc.storage.multipart.MultipartSession;
import cn.slainc.storage.multipart.MultipartUploadTemplate;
import cn.slainc.storage.multipart.PartSource;
import cn.slainc.storage.s3.S3FileStorage;
import cn.slainc.storage.s3.S3StorageFactory;
import com.adobe.testing.s3mock.S3MockApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class S3CompatibleBackendIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(S3CompatibleBackendIntegrationTest.class);

    private static final String ACCESS_KEY = "foo";

    private static final String SECRET_KEY = "bar";

    private static final String BUCKET = "modality-it";

    private static final long PART_SIZE = 5L * 1024L * 1024L;

    private static S3MockApplication s3Mock;

    private static String endpoint;

    private static S3FileStorage s3;

    private static MinioFileStorage minio;

    @BeforeAll
    static void setUp() throws IOException {
        // S3Mock 自身是一个 Spring Boot 应用，会从测试类路径加载本仓库的自动配置；
        // 其中 web 模块的 ApiResponseAdvice 会把 S3Mock 的 XML 应答包装成统一 Result 结构，导致协议解析全部失败。
        // 因此这里显式排除类路径上所有 cn.slainc.* 自动配置后再启动。
        String excludes = modalityAutoConfigurations();
        Map<String, Object> properties = new HashMap<>();
        properties.put(S3MockApplication.PROP_HTTPS_PORT, 0);
        properties.put(S3MockApplication.PROP_HTTP_PORT, 0);
        properties.put(S3MockApplication.PROP_SILENT, true);
        properties.put("spring.autoconfigure.exclude", excludes);
        properties.put("com.adobe.testing.s3mock.store.initialBuckets", BUCKET);
        s3Mock = S3MockApplication.start(properties);
        endpoint = "http://localhost:" + s3Mock.getHttpPort();
        log.info("S3Mock 真实服务端地址：{}，已排除的自动配置：{}", endpoint, excludes);
        StorageProperties.S3 s3Properties = new StorageProperties.S3();
        s3Properties.setEndpoint(endpoint);
        s3Properties.setAccessKey(ACCESS_KEY);
        s3Properties.setSecretKey(SECRET_KEY);
        s3Properties.setBucket(BUCKET);
        s3Properties.setRegion("us-east-1");
        s3Properties.setPathStyle(true);
        s3Properties.setPresignEnabled(true);
        s3 = S3StorageFactory.create("s3", s3Properties);
        s3.createBucket(BUCKET);

        StorageProperties.Minio minioProperties = new StorageProperties.Minio();
        minioProperties.setEndpoint(endpoint);
        minioProperties.setAccessKey(ACCESS_KEY);
        minioProperties.setSecretKey(SECRET_KEY);
        minioProperties.setBucket(BUCKET);
        minioProperties.setRegion("us-east-1");
        minio = MinioStorageFactory.create("minio", minioProperties);
    }

    @AfterAll
    static void stopS3Mock() {
        if (s3Mock != null) {
            s3Mock.stop();
        }
    }

    @Test
    @DisplayName("真实 S3 协议：AWS SDK 后端建桶/上传/下载/元数据/存在性/列举/删除")
    void awsSdkRoundTrip() throws IOException {
        assertThat(s3.bucketExists(BUCKET)).isTrue();
        assertThat(s3.listBuckets()).contains(BUCKET);

        byte[] data = "真实 S3 后端内容".getBytes(StandardCharsets.UTF_8);
        s3.upload("it/aws.txt", new ByteArrayInputStream(data), data.length, "text/plain");
        assertThat(s3.exists("it/aws.txt")).isTrue();
        try (InputStream input = s3.download("it/aws.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(data);
        }
        StorageMetadata metadata = s3.metadata("it/aws.txt");
        assertThat(metadata.size()).isEqualTo(data.length);
        assertThat(metadata.contentType()).isEqualTo("text/plain");
        assertThat(s3.list("it/")).extracting(StorageObject::key).contains("it/aws.txt");
        assertThat(s3.delete("it/aws.txt")).isTrue();
        assertThat(s3.exists("it/aws.txt")).isFalse();
    }

    @Test
    @DisplayName("真实 S3 协议：AWS SDK 分片上传 + 断点续传（列出已上传分片）+ 合并")
    void awsSdkMultipartResume() throws IOException {
        byte[] data = randomBytes((int) (PART_SIZE * 2 + 1024));
        MultipartUploadTemplate template = new MultipartUploadTemplate(s3, (int) PART_SIZE);
        MultipartSession session = template.init("it/aws-multipart.bin", data.length, "application/octet-stream");
        assertThat(session.totalParts()).isEqualTo(3);

        template.uploadPart(session, 1, new ByteArrayInputStream(data, 0, (int) PART_SIZE), PART_SIZE);
        List<PartInfo> uploaded = template.listParts(session);
        assertThat(uploaded).hasSize(1);
        assertThat(uploaded.get(0).partNumber()).isEqualTo(1);
        assertThat(uploaded.get(0).etag()).isNotBlank();

        MultipartSession resumed = template.resume(new MultipartSession("it/aws-multipart.bin", session.uploadId(),
                data.length, (int) PART_SIZE, "application/octet-stream"));
        assertThat(resumed.pendingPartNumbers()).containsExactly(2, 3);
        template.uploadMissingParts(resumed, PartSource.of(data), null);
        template.complete(resumed);

        try (InputStream input = s3.download("it/aws-multipart.bin")) {
            assertThat(input.readAllBytes()).isEqualTo(data);
        }
    }

    @Test
    @DisplayName("真实 S3 协议：AWS SDK 预签名下载/上传地址可用")
    void awsSdkPresignedUrl() throws Exception {
        byte[] data = "预签名内容".getBytes(StandardCharsets.UTF_8);
        s3.upload("it/presign.txt", new ByteArrayInputStream(data), data.length, "text/plain");

        String download = s3.presignedDownloadUrl("it/presign.txt", Duration.ofMinutes(5));
        HttpResponse<byte[]> response = httpGet(download);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(data);

        String upload = s3.presignedUploadUrl("it/presign-put.txt", Duration.ofMinutes(5));
        byte[] pushed = "前端直传内容".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> put = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(upload)).PUT(HttpRequest.BodyPublishers.ofByteArray(pushed)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(put.statusCode()).isEqualTo(200);
        try (InputStream input = s3.download("it/presign-put.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(pushed);
        }
    }

    @Test
    @DisplayName("真实 S3 协议：MinIO SDK 后端建桶/上传/下载/元数据/存在性/列举/删除")
    void minioRoundTrip() throws IOException {
        assertThat(minio.bucketExists(BUCKET)).isTrue();
        assertThat(minio.listBuckets()).contains(BUCKET);

        byte[] data = "MinIO SDK 写入内容".getBytes(StandardCharsets.UTF_8);
        minio.upload("it/minio.txt", new ByteArrayInputStream(data), data.length, "text/plain");
        assertThat(minio.exists("it/minio.txt")).isTrue();
        try (InputStream input = minio.download("it/minio.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(data);
        }
        StorageMetadata metadata = minio.metadata("it/minio.txt");
        assertThat(metadata.size()).isEqualTo(data.length);
        assertThat(metadata.contentType()).isEqualTo("text/plain");
        assertThat(minio.list("it/")).extracting(StorageObject::key).contains("it/minio.txt");
        assertThat(minio.delete("it/minio.txt")).isTrue();
        assertThat(minio.exists("it/minio.txt")).isFalse();
    }

    @Test
    @DisplayName("真实 S3 协议：MinIO SDK 分片上传（服务端 compose）+ 断点续传 + 合并")
    void minioMultipartResume() throws IOException {
        byte[] data = randomBytes((int) (PART_SIZE * 2 + 2048));
        MultipartUploadTemplate template = new MultipartUploadTemplate(minio, (int) PART_SIZE);
        MultipartSession session = template.init("it/minio-multipart.bin", data.length, "application/octet-stream");

        template.uploadPart(session, 1, new ByteArrayInputStream(data, 0, (int) PART_SIZE), PART_SIZE);
        template.uploadPart(session, 2, new ByteArrayInputStream(data, (int) PART_SIZE, (int) PART_SIZE), PART_SIZE);
        List<PartInfo> uploaded = template.listParts(session);
        assertThat(uploaded).hasSize(2);
        assertThat(uploaded).extracting(PartInfo::partNumber).containsExactly(1, 2);

        MultipartSession resumed = template.resume(new MultipartSession("it/minio-multipart.bin", session.uploadId(),
                data.length, (int) PART_SIZE, "application/octet-stream"));
        assertThat(resumed.pendingPartNumbers()).containsExactly(3);
        template.uploadMissingParts(resumed, PartSource.of(data), null);
        template.complete(resumed);

        try (InputStream input = minio.download("it/minio-multipart.bin")) {
            assertThat(input.readAllBytes()).isEqualTo(data);
        }
        assertThat(minio.list("it/")).extracting(StorageObject::key).doesNotContain("it/minio-multipart.bin".replace("it/", "__parts/"));
    }

    @Test
    @DisplayName("真实 S3 协议：MinIO SDK 预签名下载地址可用")
    void minioPresignedDownload() throws Exception {
        byte[] data = "MinIO 预签名内容".getBytes(StandardCharsets.UTF_8);
        minio.upload("it/minio-presign.txt", new ByteArrayInputStream(data), data.length, "text/plain");
        String url = minio.presignedDownloadUrl("it/minio-presign.txt", Duration.ofMinutes(5));
        HttpResponse<byte[]> response = httpGet(url);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(data);
    }

    private static String modalityAutoConfigurations() throws IOException {
        ClassLoader loader = S3CompatibleBackendIntegrationTest.class.getClassLoader();
        Enumeration<URL> resources = loader
                .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        TreeSet<String> names = new TreeSet<>();
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                reader.lines().map(String::trim).filter(line -> line.startsWith("cn.slainc."))
                        .forEach(names::add);
            }
        }
        return String.join(",", names);
    }

    private static HttpResponse<byte[]> httpGet(String url) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static byte[] randomBytes(int length) {
        byte[] data = new byte[length];
        new Random(20260213L).nextBytes(data);
        return data;
    }
}