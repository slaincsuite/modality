package cn.slainc.jwt.config;

import cn.slainc.crypto.jwt.JwtAlgorithm;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = JwtProperties.PREFIX)
public class JwtProperties {

    public static final String PREFIX = "modality.jwt";

    private boolean enabled = true;

    private String issuer = "modality";

    private List<JwtAlgorithm> allowedAlgorithms = new ArrayList<>(List.of(JwtAlgorithm.HS256, JwtAlgorithm.RS256, JwtAlgorithm.ES256));

    private JwtAlgorithm defaultAlgorithm = JwtAlgorithm.HS256;

    private int retainedKeys = 3;

    private int clockSkewSeconds = 60;

    private int maxTokenLength = 4096;

    private long jwksCacheSeconds = 600;

    private Duration ttlAccess = Duration.ofHours(2);

    private Duration ttlRefresh = Duration.ofDays(30);

    private Duration ttlIdToken = Duration.ofMinutes(10);

    private Duration ttlOneShot = Duration.ofMinutes(5);

    private int notBeforeSeconds = 0;

    private List<String> audiences = new ArrayList<>();

    private boolean refreshRotation = true;

    private String revocationStore = "redis";

    private final AutoRotate autoRotate = new AutoRotate();

    private List<KeySpec> keys = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public List<JwtAlgorithm> getAllowedAlgorithms() {
        return allowedAlgorithms;
    }

    public void setAllowedAlgorithms(List<JwtAlgorithm> allowedAlgorithms) {
        this.allowedAlgorithms = allowedAlgorithms;
    }

    public JwtAlgorithm getDefaultAlgorithm() {
        return defaultAlgorithm;
    }

    public void setDefaultAlgorithm(JwtAlgorithm defaultAlgorithm) {
        this.defaultAlgorithm = defaultAlgorithm;
    }

    public int getRetainedKeys() {
        return retainedKeys;
    }

    public void setRetainedKeys(int retainedKeys) {
        this.retainedKeys = retainedKeys;
    }

    public int getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(int clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public int getMaxTokenLength() {
        return maxTokenLength;
    }

    public void setMaxTokenLength(int maxTokenLength) {
        this.maxTokenLength = maxTokenLength;
    }

    public long getJwksCacheSeconds() {
        return jwksCacheSeconds;
    }

    public void setJwksCacheSeconds(long jwksCacheSeconds) {
        this.jwksCacheSeconds = jwksCacheSeconds;
    }

    public Duration getTtlAccess() {
        return ttlAccess;
    }

    public void setTtlAccess(Duration ttlAccess) {
        this.ttlAccess = ttlAccess;
    }

    public Duration getTtlRefresh() {
        return ttlRefresh;
    }

    public void setTtlRefresh(Duration ttlRefresh) {
        this.ttlRefresh = ttlRefresh;
    }

    public Duration getTtlIdToken() {
        return ttlIdToken;
    }

    public void setTtlIdToken(Duration ttlIdToken) {
        this.ttlIdToken = ttlIdToken;
    }

    public Duration getTtlOneShot() {
        return ttlOneShot;
    }

    public void setTtlOneShot(Duration ttlOneShot) {
        this.ttlOneShot = ttlOneShot;
    }

    public int getNotBeforeSeconds() {
        return notBeforeSeconds;
    }

    public void setNotBeforeSeconds(int notBeforeSeconds) {
        this.notBeforeSeconds = notBeforeSeconds;
    }

    public List<String> getAudiences() {
        return audiences;
    }

    public void setAudiences(List<String> audiences) {
        this.audiences = audiences;
    }

    public boolean isRefreshRotation() {
        return refreshRotation;
    }

    public void setRefreshRotation(boolean refreshRotation) {
        this.refreshRotation = refreshRotation;
    }

    public String getRevocationStore() {
        return revocationStore;
    }

    public void setRevocationStore(String revocationStore) {
        this.revocationStore = revocationStore;
    }

    public AutoRotate getAutoRotate() {
        return autoRotate;
    }

    public List<KeySpec> getKeys() {
        return keys;
    }

    public void setKeys(List<KeySpec> keys) {
        this.keys = keys;
    }

    public static class KeySpec {

        private String kid;

        private JwtAlgorithm algorithm;

        private String privateKeyPem;

        private String secretBase64;

        public String getKid() {
            return kid;
        }

        public void setKid(String kid) {
            this.kid = kid;
        }

        public JwtAlgorithm getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(JwtAlgorithm algorithm) {
            this.algorithm = algorithm;
        }

        public String getPrivateKeyPem() {
            return privateKeyPem;
        }

        public void setPrivateKeyPem(String privateKeyPem) {
            this.privateKeyPem = privateKeyPem;
        }

        public String getSecretBase64() {
            return secretBase64;
        }

        public void setSecretBase64(String secretBase64) {
            this.secretBase64 = secretBase64;
        }
    }

    public static class AutoRotate {

        private boolean enabled = false;

        private long intervalHours = 720;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIntervalHours() {
            return intervalHours;
        }

        public void setIntervalHours(long intervalHours) {
            this.intervalHours = intervalHours;
        }
    }
}
