package cn.slainc.storage;

import cn.slainc.storage.dedup.DedupWrapper;
import cn.slainc.storage.local.LocalFileStorage;
import cn.slainc.storage.metadata.MetadataStore;
import cn.slainc.storage.metrics.StorageMetricsRecorder;
import cn.slainc.storage.model.StorageMetadata;
import cn.slainc.storage.model.UploadRequest;
import cn.slainc.storage.model.UploadResult;
import cn.slainc.storage.quota.StorageQuotaService;
import cn.slainc.storage.routing.RoutingFileStorage;
import cn.slainc.storage.routing.StorageBackendRegistry;
import cn.slainc.storage.support.DelegatingFileStorage;
import cn.slainc.storage.support.MutableDynamicConfigService;
import cn.slainc.storage.url.RefererValidator;
import cn.slainc.storage.url.SignedUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageTemplateTest {

    @TempDir
    Path root;

    private StorageBackendRegistry registry;

    private MutableDynamicConfigService config;

    private RoutingFileStorage routing;

    private StorageTemplate template;

    private StorageQuotaService quotaService;

    @BeforeEach
    void setUp() {
        registry = new StorageBackendRegistry();
        registry.register(new LocalFileStorage("local", root, null));
        config = new MutableDynamicConfigService();
        config.put("modality.storage.backend", "local");
        DedupWrapper dedup = new DedupWrapper();
        routing = new RoutingFileStorage(registry, config, "local", List.of(dedup));
        quotaService = new StorageQuotaService(StorageQuotaService.Limits.of(1024L * 1024L, 512L * 1024L, 0.8d),
                config);
        SignedUrlService signedUrlService = new SignedUrlService(routing,
                "test-secret-for-signed-url-0123456789".getBytes(StandardCharsets.UTF_8),
                "https://storage.example.com", Duration.ofMinutes(5),
                new RefererValidator(List.of("example.com", "*.trusted.cn"), false));
        template = new StorageTemplate(routing, registry, quotaService, StorageMetricsRecorder.NOOP, dedup,
                signedUrlService);
    }

    @Test
    @DisplayName("按文件名生成租户/日期分目录的 key 并写入元数据")
    void uploadGeneratesKeyAndMetadata() throws IOException {
        byte[] data = "上传内容".getBytes(StandardCharsets.UTF_8);
        UploadResult result = template.upload(new ByteArrayInputStream(data),
                UploadRequest.of("report.txt", "text/plain").size(data.length).uploaderName("张三"));

        assertThat(result.key()).contains("/").endsWith(".txt");
        assertThat(result.backend()).isEqualTo("local");
        StorageMetadata metadata = template.metadata(result.key());
        assertThat(metadata.size()).isEqualTo(data.length);
        assertThat(metadata.uploaderName()).isEqualTo("张三");
        assertThat(metadata.tenantId()).isNotNull();
    }

    @Test
    @DisplayName("超出配额时拒绝上传并给出中文异常")
    void quotaRejectsUpload() {
        StorageQuotaService small = new StorageQuotaService(StorageQuotaService.Limits.of(64L, 16L, 0.8d), config);
        StorageTemplate restricted = new StorageTemplate(routing, registry, small, StorageMetricsRecorder.NOOP,
                new DedupWrapper(), signedUrlService());
        byte[] data = new byte[32];
        assertThatThrownBy(() -> restricted.upload(new ByteArrayInputStream(data),
                UploadRequest.of("a.bin", "application/octet-stream").size(32).tenantId(1L).uploaderId(9L)))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("用户存储配额不足");
        assertThatThrownBy(() -> restricted.upload(new ByteArrayInputStream(data),
                UploadRequest.of("b.bin", "application/octet-stream").size(100).tenantId(1L).uploaderId(8L)))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("租户存储配额不足");
    }

    @Test
    @DisplayName("接近配额上限时给出告警用量")
    void quotaWarningRatio() {
        StorageQuotaService small = new StorageQuotaService(StorageQuotaService.Limits.of(100L, 10L, 0.8d), config);
        StorageTemplate restricted = new StorageTemplate(routing, registry, small, StorageMetricsRecorder.NOOP,
                new DedupWrapper(), signedUrlService());
        byte[] data = new byte[9];
        restricted.upload(new ByteArrayInputStream(data),
                UploadRequest.of("a.bin", "application/octet-stream").size(9).uploaderId(9L).tenantId(1L));
        assertThat(small.userUsage(9L).used()).isEqualTo(9);
        assertThat(small.userUsage(9L).warning()).isTrue();
        assertThat(small.tenantUsage(1L).warning()).isFalse();
    }

    private SignedUrlService signedUrlService() {
        return new SignedUrlService(routing, "test-secret-for-signed-url-0123456789".getBytes(StandardCharsets.UTF_8),
                "https://storage.example.com", Duration.ofMinutes(5), null);
    }

    @Test
    @DisplayName("临时下载地址带有效期，签名被篡改或过期时校验失败")
    void signedUrlLifecycle() {
        byte[] data = "签名内容".getBytes(StandardCharsets.UTF_8);
        UploadResult result = template.upload(new ByteArrayInputStream(data),
                UploadRequest.of("s.txt", "text/plain").size(data.length).key("signed/s.txt"));

        String url = template.signedDownloadUrl(result.key(), Duration.ofMinutes(5));
        assertThat(url).startsWith("https://storage.example.com/").contains("expires=").contains("signature=");

        String query = url.substring(url.indexOf('?') + 1);
        String expires = query.substring(query.indexOf("expires=") + 8, query.indexOf("&signature="));
        String signature = query.substring(query.indexOf("&signature=") + 11);
        SignedUrlService service = new SignedUrlService(routing,
                "test-secret-for-signed-url-0123456789".getBytes(StandardCharsets.UTF_8),
                "https://storage.example.com", Duration.ofMinutes(5), null);
        service.verify(result.key(), "GET", Long.parseLong(expires), signature);
        assertThatThrownBy(() -> service.verify(result.key(), "GET", Long.parseLong(expires), "deadbeef"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("签名校验失败");
        assertThatThrownBy(() -> service.verify(result.key(), "GET", 1L, signature))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("已过期");
    }

    @Test
    @DisplayName("防盗链：非白名单 Referer 被拒绝")
    void refererGuard() {
        RefererValidator validator = new RefererValidator(List.of("example.com", "*.trusted.cn"), false);
        validator.validate("https://example.com/page");
        validator.validate("https://a.trusted.cn/page");
        assertThatThrownBy(() -> validator.validate("https://evil.com/page"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("防盗链校验未通过");
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(StorageException.class)
                .hasMessageContaining("防盗链校验未通过");
    }

    @Test
    @DisplayName("打包下载：多文件 ZIP 内容正确")
    void zipDownload() throws IOException {
        byte[] first = "一".getBytes(StandardCharsets.UTF_8);
        byte[] second = "二".getBytes(StandardCharsets.UTF_8);
        template.upload(new ByteArrayInputStream(first),
                UploadRequest.of("1.txt", "text/plain").size(first.length).key("zip/1.txt"));
        template.upload(new ByteArrayInputStream(second),
                UploadRequest.of("2.txt", "text/plain").size(second.length).key("zip/2.txt"));

        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        long written = template.zip(List.of("zip/1.txt", "zip/2.txt"), output);
        assertThat(written).isGreaterThan(0L);

        java.util.Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new ByteArrayInputStream(output.toByteArray()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        assertThat(entries).containsKeys("1.txt", "2.txt");
        assertThat(entries.get("1.txt")).isEqualTo(first);
        assertThat(entries.get("2.txt")).isEqualTo(second);
    }

    @Test
    @DisplayName("过渡期双写：主后端与副后端同时写入，副后端失败不影响主写入")
    void dualWriteDuringMigration() throws IOException {
        LocalFileStorage secondary = new LocalFileStorage("secondary", root.resolve("secondary"), null);
        registry.register(secondary);
        config.put("modality.storage.dual-write-enabled", "true");
        config.put("modality.storage.dual-write-backend", "secondary");

        byte[] data = "双写内容".getBytes(StandardCharsets.UTF_8);
        UploadResult result = template.upload(new ByteArrayInputStream(data),
                UploadRequest.of("d.txt", "text/plain").size(data.length).key("dual/d.txt"));

        assertThat(result.backend()).isEqualTo("local");
        assertThat(secondary.exists("dual/d.txt")).isTrue();
        try (InputStream input = secondary.download("dual/d.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(data);
        }
        try (InputStream input = template.download("dual/d.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(data);
        }
        assertThat(template.delete("dual/d.txt")).isTrue();
        assertThat(secondary.exists("dual/d.txt")).isFalse();
    }

    @Test
    @DisplayName("图片元数据只读受限头部：超过阈值直接跳过，不整图下载")
    void imageMetadataIsBoundedByHeadLimit() throws IOException {
        CountingStorage counting = new CountingStorage(new LocalFileStorage("counting", root.resolve("counting"),
                null));
        StorageBackendRegistry countingRegistry = new StorageBackendRegistry();
        countingRegistry.register(counting);
        RoutingFileStorage countingRouting = new RoutingFileStorage(countingRegistry, null, "counting", List.of());
        StorageTemplate bounded = new StorageTemplate(countingRouting, countingRegistry, null,
                StorageMetricsRecorder.NOOP, null, signedUrlService(), MetadataStore.NOOP, 16);

        byte[] oversized = new byte[4096];
        bounded.upload(new ByteArrayInputStream(oversized),
                UploadRequest.of("huge.png", "image/png").size(oversized.length).key("img/huge.png"));
        StorageMetadata skipped = bounded.metadata("img/huge.png");
        assertThat(skipped.image()).isNull();
        assertThat(counting.downloadCalls()).isZero();

        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(4, 3,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", png);
        byte[] small = png.toByteArray();
        bounded.upload(new ByteArrayInputStream(small),
                UploadRequest.of("small.png", "image/png").size(small.length).key("img/small.png"));

        StorageTemplate generous = new StorageTemplate(countingRouting, countingRegistry, null,
                StorageMetricsRecorder.NOOP, null, signedUrlService(), MetadataStore.NOOP, 1024);
        StorageMetadata parsed = generous.metadata("img/small.png");
        assertThat(parsed.image()).isNotNull();
        assertThat(parsed.image().width()).isEqualTo(4);
        assertThat(parsed.image().height()).isEqualTo(3);
        assertThat(counting.downloadCalls()).isEqualTo(1);
    }

    private static final class CountingStorage extends DelegatingFileStorage {

        private final java.util.concurrent.atomic.AtomicInteger downloads = new java.util.concurrent.atomic.AtomicInteger();

        private CountingStorage(cn.slainc.storage.ExtendedFileStorage delegate) {
            super(delegate);
        }

        @Override
        public InputStream download(String key) {
            downloads.incrementAndGet();
            return delegate().download(key);
        }

        private int downloadCalls() {
            return downloads.get();
        }
    }

    @Test
    @DisplayName("秒传统计在模板层可见")
    void instantUploadStatVisible() {
        byte[] data = "重复内容".getBytes(StandardCharsets.UTF_8);
        template.upload(new ByteArrayInputStream(data),
                UploadRequest.of("a.txt", "text/plain").size(data.length).key("dup/a.txt"));
        template.upload(new ByteArrayInputStream(data),
                UploadRequest.of("b.txt", "text/plain").size(data.length).key("dup/b.txt"));
        var stat = template.instantUploadStat();
        assertThat(stat.attempts()).isEqualTo(2);
        assertThat(stat.hits()).isEqualTo(1);
    }
}