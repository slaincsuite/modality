package cn.slainc.storage.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.storage")
public class StorageProperties {

    private boolean enabled = true;

    private String backend = "local";

    private final Local local = new Local();

    private final S3 s3 = new S3();

    private final Minio minio = new Minio();

    private final SignedUrl signedUrl = new SignedUrl();

    private final Encryption encryption = new Encryption();

    private final Antivirus antivirus = new Antivirus();

    private final Quota quota = new Quota();

    private final Video video = new Video();

    private final Preview preview = new Preview();

    private final Lifecycle lifecycle = new Lifecycle();

    private final Dedup dedup = new Dedup();

    private boolean dualWriteEnabled;

    private String dualWriteBackend;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public Local getLocal() {
        return local;
    }

    public S3 getS3() {
        return s3;
    }

    public Minio getMinio() {
        return minio;
    }

    public SignedUrl getSignedUrl() {
        return signedUrl;
    }

    public Encryption getEncryption() {
        return encryption;
    }

    public Antivirus getAntivirus() {
        return antivirus;
    }

    public Quota getQuota() {
        return quota;
    }

    public Video getVideo() {
        return video;
    }

    public Preview getPreview() {
        return preview;
    }

    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    public Dedup getDedup() {
        return dedup;
    }

    public boolean isDualWriteEnabled() {
        return dualWriteEnabled;
    }

    public void setDualWriteEnabled(boolean dualWriteEnabled) {
        this.dualWriteEnabled = dualWriteEnabled;
    }

    public String getDualWriteBackend() {
        return dualWriteBackend;
    }

    public void setDualWriteBackend(String dualWriteBackend) {
        this.dualWriteBackend = dualWriteBackend;
    }

    public static class Local {

        private boolean enabled = true;

        private String root;

        private String baseUrl;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class S3 {

        private boolean enabled;

        private String endpoint;

        private String region = "us-east-1";

        private String accessKey;

        private String secretKey;

        private String bucket = "modality";

        private boolean pathStyle = true;

        private boolean presignEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public boolean isPathStyle() {
            return pathStyle;
        }

        public void setPathStyle(boolean pathStyle) {
            this.pathStyle = pathStyle;
        }

        public boolean isPresignEnabled() {
            return presignEnabled;
        }

        public void setPresignEnabled(boolean presignEnabled) {
            this.presignEnabled = presignEnabled;
        }
    }

    public static class Minio {

        private boolean enabled;

        private String endpoint;

        private String accessKey;

        private String secretKey;

        private String bucket = "modality";

        private String region;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }
    }

    public static class SignedUrl {

        private String secret;

        private String baseUrl;

        private Duration ttl = Duration.ofMinutes(15);

        private List<String> refererAllowedHosts = new ArrayList<>();

        private boolean refererAllowEmpty;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public List<String> getRefererAllowedHosts() {
            return refererAllowedHosts;
        }

        public void setRefererAllowedHosts(List<String> refererAllowedHosts) {
            this.refererAllowedHosts = refererAllowedHosts;
        }

        public boolean isRefererAllowEmpty() {
            return refererAllowEmpty;
        }

        public void setRefererAllowEmpty(boolean refererAllowEmpty) {
            this.refererAllowEmpty = refererAllowEmpty;
        }
    }

    public static class Encryption {

        private boolean enabled;

        private String key;

        private String keyId = "storage";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }
    }

    public static class Antivirus {

        private boolean enabled;

        private String host = "127.0.0.1";

        private int port = 3310;

        private Strategy strategy = Strategy.SYNC;

        private boolean failClosed;

        private Duration timeout = Duration.ofSeconds(10);

        private String quarantineDirectory;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public Strategy getStrategy() {
            return strategy;
        }

        public void setStrategy(Strategy strategy) {
            this.strategy = strategy;
        }

        public boolean isFailClosed() {
            return failClosed;
        }

        public void setFailClosed(boolean failClosed) {
            this.failClosed = failClosed;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public String getQuarantineDirectory() {
            return quarantineDirectory;
        }

        public void setQuarantineDirectory(String quarantineDirectory) {
            this.quarantineDirectory = quarantineDirectory;
        }
    }

    public enum Strategy {
        SYNC,
        ASYNC
    }

    public static class Quota {

        private boolean enabled;

        private long tenantLimit;

        private long userLimit;

        private double warningRatio = 0.8d;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTenantLimit() {
            return tenantLimit;
        }

        public void setTenantLimit(long tenantLimit) {
            this.tenantLimit = tenantLimit;
        }

        public long getUserLimit() {
            return userLimit;
        }

        public void setUserLimit(long userLimit) {
            this.userLimit = userLimit;
        }

        public double getWarningRatio() {
            return warningRatio;
        }

        public void setWarningRatio(double warningRatio) {
            this.warningRatio = warningRatio;
        }
    }

    public static class Video {

        private String ffmpeg = "ffmpeg";

        private String ffprobe = "ffprobe";

        private Duration timeout = Duration.ofMinutes(10);

        public String getFfmpeg() {
            return ffmpeg;
        }

        public void setFfmpeg(String ffmpeg) {
            this.ffmpeg = ffmpeg;
        }

        public String getFfprobe() {
            return ffprobe;
        }

        public void setFfprobe(String ffprobe) {
            this.ffprobe = ffprobe;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static class Preview {

        private String libreOffice = "soffice";

        private Duration timeout = Duration.ofMinutes(2);

        public String getLibreOffice() {
            return libreOffice;
        }

        public void setLibreOffice(String libreOffice) {
            this.libreOffice = libreOffice;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static class Lifecycle {

        private boolean enabled;

        private Duration fixedDelay = Duration.ofHours(6);

        private List<Rule> rules = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getFixedDelay() {
            return fixedDelay;
        }

        public void setFixedDelay(Duration fixedDelay) {
            this.fixedDelay = fixedDelay;
        }

        public List<Rule> getRules() {
            return rules;
        }

        public void setRules(List<Rule> rules) {
            this.rules = rules;
        }
    }

    public static class Rule {

        private String name;

        private String prefix;

        private Duration expireAfter;

        private Duration archiveAfter;

        private String archiveBackend;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public Duration getExpireAfter() {
            return expireAfter;
        }

        public void setExpireAfter(Duration expireAfter) {
            this.expireAfter = expireAfter;
        }

        public Duration getArchiveAfter() {
            return archiveAfter;
        }

        public void setArchiveAfter(Duration archiveAfter) {
            this.archiveAfter = archiveAfter;
        }

        public String getArchiveBackend() {
            return archiveBackend;
        }

        public void setArchiveBackend(String archiveBackend) {
            this.archiveBackend = archiveBackend;
        }
    }

    public static class Dedup {

        private boolean enabled = true;

        private String registryDirectory;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRegistryDirectory() {
            return registryDirectory;
        }

        public void setRegistryDirectory(String registryDirectory) {
            this.registryDirectory = registryDirectory;
        }
    }
}
