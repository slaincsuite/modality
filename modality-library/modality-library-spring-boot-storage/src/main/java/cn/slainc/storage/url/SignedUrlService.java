package cn.slainc.storage.url;

import cn.slainc.crypto.hmac.HmacAlgorithm;
import cn.slainc.crypto.hmac.HmacUtils;
import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.model.SignedUrl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class SignedUrlService {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private final ExtendedFileStorage storage;

    private final byte[] secret;

    private final String baseUrl;

    private final Duration defaultTtl;

    private final RefererValidator refererValidator;

    public SignedUrlService(ExtendedFileStorage storage, byte[] secret, String baseUrl, Duration defaultTtl,
                            RefererValidator refererValidator) {
        this.storage = storage;
        this.secret = secret == null ? new byte[0] : secret.clone();
        this.baseUrl = baseUrl;
        this.defaultTtl = defaultTtl == null ? DEFAULT_TTL : defaultTtl;
        this.refererValidator = refererValidator;
    }

    public SignedUrl uploadUrl(String key, Duration ttl) {
        return localUrl(key, "PUT", ttl);
    }

    public SignedUrl downloadUrl(String key, Duration ttl) {
        if (storage.supportsSignedUrl()) {
            Duration effective = ttl == null ? defaultTtl : ttl;
            String url = storage.presignedDownloadUrl(key, effective);
            return new SignedUrl(url, "GET", key, Instant.now().plus(effective));
        }
        return localUrl(key, "GET", ttl);
    }

    public SignedUrl localUrl(String key, String method, Duration ttl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new StorageException("未配置 modality.storage.signed-url.base-url，无法生成临时访问地址");
        }
        Duration effective = ttl == null ? defaultTtl : ttl;
        long expiresAt = Instant.now().plus(effective).getEpochSecond();
        String signature = sign(key, method, expiresAt);
        String url = trimTrailingSlash(baseUrl) + "/" + key + "?method=" + method + "&expires=" + expiresAt
                + "&signature=" + signature;
        return new SignedUrl(url, method, key, Instant.ofEpochSecond(expiresAt));
    }

    public void verify(String key, String method, long expiresAt, String signature) {
        if (secret.length == 0) {
            throw new StorageException("未配置签名密钥，无法校验临时访问地址");
        }
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw new StorageException("临时访问地址已过期：" + key);
        }
        String expected = sign(key, method, expiresAt);
        if (!constantTimeEquals(expected, signature)) {
            throw new StorageException("临时访问地址签名校验失败：" + key);
        }
    }

    public void verifyReferer(String referer) {
        if (refererValidator != null) {
            refererValidator.validate(referer);
        }
    }

    public Map<String, String> queryParameters(String key, String method, Duration ttl) {
        SignedUrl url = localUrl(key, method, ttl);
        Map<String, String> parameters = new LinkedHashMap<>();
        int index = url.url().indexOf('?');
        if (index > 0) {
            for (String pair : url.url().substring(index + 1).split("&")) {
                int split = pair.indexOf('=');
                parameters.put(pair.substring(0, split), pair.substring(split + 1));
            }
        }
        return parameters;
    }

    public String sign(String key, String method, long expiresAt) {
        if (secret.length == 0) {
            throw new StorageException("未配置签名密钥，无法生成临时访问地址");
        }
        String payload = method + "\n" + key + "\n" + expiresAt;
        return HmacUtils.hmacHex(HmacAlgorithm.HMAC_SHA256, secret, payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
