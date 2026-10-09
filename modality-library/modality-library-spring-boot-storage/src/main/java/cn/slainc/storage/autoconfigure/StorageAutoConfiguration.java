package cn.slainc.storage.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.crypto.key.EnvelopeEncryption;
import cn.slainc.crypto.key.SecretKeyUtils;
import cn.slainc.crypto.symmetric.AesKeySize;
import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.StorageTemplate;
import cn.slainc.storage.antivirus.AntivirusService;
import cn.slainc.storage.antivirus.ClamAvClient;
import cn.slainc.storage.antivirus.QuarantineStore;
import cn.slainc.storage.command.ExternalCommandRunner;
import cn.slainc.storage.crypto.StorageKeyManager;
import cn.slainc.storage.dedup.DedupRegistry;
import cn.slainc.storage.dedup.DedupWrapper;
import cn.slainc.storage.dedup.SidecarDedupRegistry;
import cn.slainc.storage.lifecycle.LifecycleRule;
import cn.slainc.storage.lifecycle.StorageLifecycleService;
import cn.slainc.storage.local.LocalFileStorage;
import cn.slainc.storage.metrics.StorageMetricsRecorder;
import cn.slainc.storage.preview.FilePreviewService;
import cn.slainc.storage.quota.StorageQuotaService;
import cn.slainc.storage.routing.RoutingFileStorage;
import cn.slainc.storage.routing.StorageBackendRegistrar;
import cn.slainc.storage.routing.StorageBackendRegistry;
import cn.slainc.storage.support.AntivirusStorageWrapper;
import cn.slainc.storage.support.EncryptingStorageWrapper;
import cn.slainc.storage.support.MetricsStorageWrapper;
import cn.slainc.storage.support.StorageWrapper;
import cn.slainc.storage.url.RefererValidator;
import cn.slainc.storage.url.SignedUrlService;
import cn.slainc.storage.video.FfmpegCommandBuilder;
import cn.slainc.storage.video.FfprobeCommandBuilder;
import cn.slainc.storage.video.VideoMetadataReader;
import cn.slainc.storage.video.VideoProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(prefix = "modality.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StorageAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.storage.local", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public LocalFileStorage localFileStorage(StorageProperties properties) {
        StorageProperties.Local local = properties.getLocal();
        Path root = local.getRoot() == null || local.getRoot().isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "modality-storage")
                : Path.of(local.getRoot());
        log.info("本地对象存储根目录：{}", root.toAbsolutePath());
        return new LocalFileStorage("local", root, local.getBaseUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    public StorageBackendRegistry storageBackendRegistry() {
        return new StorageBackendRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public StorageBackendRegistrar storageBackendRegistrar(StorageBackendRegistry registry,
                                                           ObjectProvider<ExtendedFileStorage> storages) {
        return new StorageBackendRegistrar(registry, storages);
    }

    @Bean
    @ConditionalOnMissingBean
    @Order(0)
    @ConditionalOnProperty(prefix = "modality.storage.encryption", name = "enabled", havingValue = "true")
    public EncryptingStorageWrapper storageEncryptingWrapper(StorageProperties properties) {
        StorageProperties.Encryption encryption = properties.getEncryption();
        SecretKey kek = encryption.getKey() == null || encryption.getKey().isBlank()
                ? SecretKeyUtils.aesKey(AesKeySize.AES_256)
                : SecretKeyUtils.decode(encryption.getKey(), "AES");
        if (encryption.getKey() == null || encryption.getKey().isBlank()) {
            log.warn("未配置 modality.storage.encryption.key，已生成临时 KEK；进程重启后已加密对象将无法解密，生产环境必须显式配置");
        }
        EnvelopeEncryption envelope = new EnvelopeEncryption(kek, encryption.getKeyId());
        Path keyFile = Path.of(System.getProperty("java.io.tmpdir"), "modality-storage", ".keys", "dek.enc");
        return new EncryptingStorageWrapper(new StorageKeyManager(envelope, keyFile));
    }

    @Bean
    @ConditionalOnMissingBean
    @Order(10)
    @ConditionalOnProperty(prefix = "modality.storage.antivirus", name = "enabled", havingValue = "true")
    public AntivirusStorageWrapper storageAntivirusWrapper(StorageProperties properties,
                                                           AntivirusService antivirusService) {
        return new AntivirusStorageWrapper(antivirusService);
    }

    @Bean
    @ConditionalOnMissingBean
    @Order(20)
    public MetricsStorageWrapper storageMetricsWrapper(ObjectProvider<StorageMetricsRecorder> metrics) {
        return new MetricsStorageWrapper(metrics.getIfAvailable(() -> StorageMetricsRecorder.NOOP));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.storage.dedup", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public DedupRegistry storageDedupRegistry(StorageProperties properties) {
        String directory = properties.getDedup().getRegistryDirectory();
        Path root = directory == null || directory.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "modality-storage", "dedup")
                : Path.of(directory);
        return new SidecarDedupRegistry(root);
    }

    @Bean
    @ConditionalOnMissingBean
    @Order(30)
    @ConditionalOnProperty(prefix = "modality.storage.dedup", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public DedupWrapper storageDedupWrapper(DedupRegistry dedupRegistry) {
        return new DedupWrapper(dedupRegistry);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public RoutingFileStorage routingFileStorage(StorageBackendRegistry registry,
                                                 ObjectProvider<DynamicConfigService> configService,
                                                 ObjectProvider<StorageWrapper> wrappers,
                                                 StorageProperties properties) {
        return new RoutingFileStorage(registry, configService.getIfAvailable(), properties.getBackend(),
                wrappers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public SignedUrlService signedUrlService(RoutingFileStorage fileStorage, StorageProperties properties) {
        StorageProperties.SignedUrl signed = properties.getSignedUrl();
        byte[] secret;
        if (signed.getSecret() == null || signed.getSecret().isBlank()) {
            secret = SecretKeyUtils.randomKey(32);
            log.warn("未配置 modality.storage.signed-url.secret，已生成临时签名密钥；进程重启后既有临时地址全部失效");
        } else {
            secret = signed.getSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return new SignedUrlService(fileStorage, secret, signed.getBaseUrl(), signed.getTtl(),
                new RefererValidator(signed.getRefererAllowedHosts(), signed.isRefererAllowEmpty()));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.storage.quota", name = "enabled", havingValue = "true")
    public StorageQuotaService storageQuotaService(StorageProperties properties,
                                                   ObjectProvider<DynamicConfigService> configService) {
        StorageProperties.Quota quota = properties.getQuota();
        return new StorageQuotaService(StorageQuotaService.Limits.of(quota.getTenantLimit(), quota.getUserLimit(),
                quota.getWarningRatio()), configService.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public StorageTemplate storageTemplate(RoutingFileStorage routingFileStorage, StorageBackendRegistry registry,
                                           ObjectProvider<StorageQuotaService> quotaService,
                                           ObjectProvider<StorageMetricsRecorder> metrics,
                                           ObjectProvider<DedupWrapper> dedupWrapper,
                                           SignedUrlService signedUrlService) {
        return new StorageTemplate(routingFileStorage, registry, quotaService.getIfAvailable(),
                metrics.getIfAvailable(() -> StorageMetricsRecorder.NOOP), dedupWrapper.getIfAvailable(),
                signedUrlService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExternalCommandRunner storageCommandRunner(StorageProperties properties) {
        return new ExternalCommandRunner(properties.getVideo().getTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    public VideoProcessor videoProcessor(StorageProperties properties, ExternalCommandRunner runner) {
        return new VideoProcessor(new FfmpegCommandBuilder(properties.getVideo().getFfmpeg()), runner,
                properties.getVideo().getTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    public VideoMetadataReader videoMetadataReader(StorageProperties properties, ExternalCommandRunner runner) {
        return new VideoMetadataReader(new FfprobeCommandBuilder(properties.getVideo().getFfprobe()), runner);
    }

    @Bean
    @ConditionalOnMissingBean
    public FilePreviewService filePreviewService(RoutingFileStorage fileStorage, ExternalCommandRunner runner,
                                                 StorageProperties properties) {
        return new FilePreviewService(fileStorage, runner, properties.getPreview().getLibreOffice(),
                properties.getPreview().getTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.storage.antivirus", name = "enabled", havingValue = "true")
    public AntivirusService antivirusService(StorageProperties properties) {
        StorageProperties.Antivirus antivirus = properties.getAntivirus();
        Path quarantine = antivirus.getQuarantineDirectory() == null || antivirus.getQuarantineDirectory().isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "modality-storage", "quarantine")
                : Path.of(antivirus.getQuarantineDirectory());
        return new AntivirusService(new ClamAvClient(antivirus.getHost(), antivirus.getPort(),
                antivirus.getTimeout()),
                new QuarantineStore(quarantine),
                antivirus.getStrategy() == StorageProperties.Strategy.ASYNC ? AntivirusService.Strategy.ASYNC
                        : AntivirusService.Strategy.SYNC,
                antivirus.isFailClosed());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.storage.lifecycle", name = "enabled", havingValue = "true")
    public StorageLifecycleService storageLifecycleService(RoutingFileStorage fileStorage,
                                                           StorageBackendRegistry registry,
                                                           StorageProperties properties) {
        List<LifecycleRule> rules = properties.getLifecycle().getRules().stream()
                .map(rule -> new LifecycleRule(rule.getName(), rule.getPrefix(), rule.getExpireAfter(),
                        rule.getArchiveAfter(), rule.getArchiveBackend()))
                .toList();
        return new StorageLifecycleService(fileStorage, registry, rules);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.storage.lifecycle", name = "enabled", havingValue = "true")
    public StorageLifecycleScheduler storageLifecycleScheduler(StorageLifecycleService lifecycleService,
                                                               StorageProperties properties) {
        return new StorageLifecycleScheduler(lifecycleService, properties.getLifecycle().getFixedDelay());
    }
}