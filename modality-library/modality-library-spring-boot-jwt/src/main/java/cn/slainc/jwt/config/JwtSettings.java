package cn.slainc.jwt.config;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.jwt.issue.TokenType;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;

public class JwtSettings {

    public static final String KEY_TTL_ACCESS = "modality.jwt.ttl-access";

    public static final String KEY_TTL_REFRESH = "modality.jwt.ttl-refresh";

    public static final String KEY_TTL_ID = "modality.jwt.ttl-id";

    public static final String KEY_TTL_ONE_SHOT = "modality.jwt.ttl-one-shot";

    public static final String KEY_CLOCK_SKEW_SECONDS = "modality.jwt.clock-skew-seconds";

    public static final String KEY_MAX_TOKEN_LENGTH = "modality.jwt.max-token-length";

    public static final String KEY_JWKS_CACHE_SECONDS = "modality.jwt.jwks-cache-seconds";

    private final JwtProperties properties;

    private final DynamicConfigService dynamicConfig;

    public JwtSettings(JwtProperties properties, ObjectProvider<DynamicConfigService> dynamicConfigProvider) {
        this(properties, dynamicConfigProvider.getIfAvailable());
    }

    public JwtSettings(JwtProperties properties, DynamicConfigService dynamicConfig) {
        this.properties = properties;
        this.dynamicConfig = dynamicConfig;
    }

    public Duration ttl(TokenType tokenType, Duration specTtl) {
        if (specTtl != null && !specTtl.isNegative() && !specTtl.isZero()) {
            return specTtl;
        }
        return ttl(tokenType);
    }

    public Duration ttl(TokenType tokenType) {
        long fallback = switch (tokenType) {
            case ACCESS -> properties.getTtlAccess().toSeconds();
            case REFRESH -> properties.getTtlRefresh().toSeconds();
            case ID_TOKEN -> properties.getTtlIdToken().toSeconds();
            case ONE_SHOT -> properties.getTtlOneShot().toSeconds();
        };
        long seconds = dynamic(dynamicKeyOf(tokenType), fallback);
        return Duration.ofSeconds(Math.max(1, seconds));
    }

    public int clockSkewSeconds() {
        return (int) dynamic(KEY_CLOCK_SKEW_SECONDS, properties.getClockSkewSeconds());
    }

    public int maxTokenLength() {
        return (int) dynamic(KEY_MAX_TOKEN_LENGTH, properties.getMaxTokenLength());
    }

    public long jwksCacheSeconds() {
        return Math.max(1, dynamic(KEY_JWKS_CACHE_SECONDS, properties.getJwksCacheSeconds()));
    }

    public String issuer() {
        return properties.getIssuer();
    }

    public List<String> audiences() {
        return properties.getAudiences();
    }

    public boolean refreshRotation() {
        return properties.isRefreshRotation();
    }

    public int notBeforeSeconds() {
        return properties.getNotBeforeSeconds();
    }

    private String dynamicKeyOf(TokenType tokenType) {
        return switch (tokenType) {
            case ACCESS -> KEY_TTL_ACCESS;
            case REFRESH -> KEY_TTL_REFRESH;
            case ID_TOKEN -> KEY_TTL_ID;
            case ONE_SHOT -> KEY_TTL_ONE_SHOT;
        };
    }

    private long dynamic(String key, long fallback) {
        if (dynamicConfig == null) {
            return fallback;
        }
        return dynamicConfig.getLong(key, fallback);
    }
}
