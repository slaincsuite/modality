package cn.slainc.oauth2.pkce;

import cn.dev33.satoken.oauth2.exception.SaOAuth2Exception;
import cn.slainc.oauth2.config.Oauth2Keys;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PkceService {

    public static final String METHOD_S256 = "S256";

    private static final String STORE_VALUE_SEPARATOR = ":";

    private static final String CHALLENGE_PATTERN = "[A-Za-z0-9_-]{43,128}";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    private final Map<String, LocalEntry> localStore = new ConcurrentHashMap<>();

    private record LocalEntry(String value, long expireAt) {
    }

    public PkceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveChallenge(String code, String method, String challenge, long ttlSeconds) {
        if (code == null || code.isBlank()) {
            throw new SaOAuth2Exception("PKCE 绑定的授权码不能为空");
        }
        if (method == null || method.isBlank()) {
            method = METHOD_S256;
        }
        if (!METHOD_S256.equalsIgnoreCase(method)) {
            throw new SaOAuth2Exception("不支持的 code_challenge_method：" + method + "，仅允许 S256");
        }
        if (challenge == null || !challenge.matches(CHALLENGE_PATTERN)) {
            throw new SaOAuth2Exception("code_challenge 必须是 43-128 位 base64url 编码");
        }
        String value = METHOD_S256 + STORE_VALUE_SEPARATOR + challenge;
        long ttl = ttlSeconds > 0 ? ttlSeconds : 300;
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(Oauth2Keys.pkceKey(code), value, java.time.Duration.ofSeconds(ttl));
        } else {
            localStore.put(Oauth2Keys.pkceKey(code), new LocalEntry(value, System.currentTimeMillis() + ttl * 1000));
        }
    }

    public boolean hasChallenge(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String key = Oauth2Keys.pkceKey(code);
        if (redisTemplate != null) {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        }
        LocalEntry entry = localStore.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.expireAt() <= System.currentTimeMillis()) {
            localStore.remove(key);
            return false;
        }
        return true;
    }

    public void verifyAndConsume(String code, String verifier) {
        String key = Oauth2Keys.pkceKey(code);
        String stored = load(key);
        if (stored == null) {
            return;
        }
        delete(key);
        if (verifier == null || verifier.isBlank()) {
            throw new SaOAuth2Exception("缺少 code_verifier 参数");
        }
        int separatorIndex = stored.indexOf(STORE_VALUE_SEPARATOR);
        String method = separatorIndex > 0 ? stored.substring(0, separatorIndex) : METHOD_S256;
        String challenge = stored.substring(separatorIndex + 1);
        String computed = challengeOf(method, verifier);
        if (!MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8), challenge.getBytes(StandardCharsets.UTF_8))) {
            throw new SaOAuth2Exception("PKCE 校验失败：code_verifier 与 code_challenge 不匹配");
        }
    }

    public String challengeOf(String method, String verifier) {
        if (!METHOD_S256.equalsIgnoreCase(method)) {
            throw new SaOAuth2Exception("不支持的 code_challenge_method：" + method + "，仅允许 S256");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(verifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new SaOAuth2Exception("计算 PKCE 摘要失败");
        }
    }

    public String randomVerifier() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String load(String key) {
        if (redisTemplate != null) {
            return redisTemplate.opsForValue().get(key);
        }
        LocalEntry entry = localStore.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt() <= System.currentTimeMillis()) {
            localStore.remove(key);
            return null;
        }
        return entry.value();
    }

    private void delete(String key) {
        if (redisTemplate != null) {
            redisTemplate.delete(key);
        } else {
            localStore.remove(key);
        }
    }
}
