package cn.slainc.sensitive.core;

import cn.slainc.crypto.digest.DigestUtils;
import cn.slainc.crypto.digest.Sm3Utils;
import cn.slainc.crypto.key.CryptoKeyProvider;
import cn.slainc.crypto.symmetric.AesUtils;
import cn.slainc.sensitive.SensitiveException;
import cn.slainc.sensitive.annotation.Sensitive;
import cn.slainc.sensitive.annotation.SensitiveType;
import cn.slainc.sensitive.metrics.SensitiveMetrics;
import cn.slainc.utils.string.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Supplier;

public class SensitiveMasker {

    public static final String ENCRYPTED_PREFIX = "enc:v1:";

    private final SensitiveConfig config;

    private final SensitiveMetrics metrics;

    private final SensitiveSwitch sensitiveSwitch;

    private final Supplier<CryptoKeyProvider> keyProvider;

    public SensitiveMasker(SensitiveConfig config, SensitiveMetrics metrics, SensitiveSwitch sensitiveSwitch,
                           Supplier<CryptoKeyProvider> keyProvider) {
        this.config = config;
        this.metrics = metrics;
        this.sensitiveSwitch = sensitiveSwitch;
        this.keyProvider = keyProvider;
    }

    public SensitiveRule rule(Sensitive annotation) {
        return SensitiveRule.of(annotation, config);
    }

    public boolean isActive(SensitiveRule rule) {
        return sensitiveSwitch.isEnabled() && SensitiveContext.matches(rule.groups());
    }

    public String mask(String raw, SensitiveRule rule) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        if (!isActive(rule)) {
            metrics.recordSkip();
            return raw;
        }
        String masked = switch (rule.strategy()) {
            case MASK -> maskByType(raw, rule);
            case REPLACE -> rule.replacement();
            case HASH -> hash(raw);
            case ENCRYPT -> encrypt(raw);
        };
        metrics.recordMask(rule.type(), rule.strategy());
        return masked;
    }

    public String hash(String raw) {
        String algorithm = config.hashAlgorithm();
        if (SensitiveConfig.HASH_SM3.equalsIgnoreCase(algorithm)) {
            return Sm3Utils.digestHex(raw);
        }
        if (SensitiveConfig.HASH_SHA256.equalsIgnoreCase(algorithm)) {
            return DigestUtils.sha256Hex(raw);
        }
        throw new SensitiveException("不支持的哈希算法：" + algorithm + "，可选 SM3 或 SHA-256");
    }

    public String encrypt(String raw) {
        CryptoKeyProvider provider = requireKeyProvider();
        SecretKey key = provider.currentKey();
        byte[] cipherText = AesUtils.encryptGcm(key.getEncoded(), raw.getBytes(StandardCharsets.UTF_8));
        return ENCRYPTED_PREFIX + provider.currentKeyId() + ":" + Base64.getEncoder().encodeToString(cipherText);
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || !encrypted.startsWith(ENCRYPTED_PREFIX)) {
            throw new SensitiveException("待解密的文本不是本模块产生的密文，缺少 " + ENCRYPTED_PREFIX + " 前缀");
        }
        String body = encrypted.substring(ENCRYPTED_PREFIX.length());
        int separator = body.indexOf(':');
        if (separator <= 0) {
            throw new SensitiveException("密文格式非法，期望 enc:v1:<密钥标识>:<Base64>");
        }
        String keyId = body.substring(0, separator);
        String payload = body.substring(separator + 1);
        byte[] cipherText;
        try {
            cipherText = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException exception) {
            throw new SensitiveException("密文 Base64 解码失败", exception);
        }
        SecretKey key = requireKeyProvider().key(keyId);
        return new String(AesUtils.decryptGcm(key.getEncoded(), cipherText), StandardCharsets.UTF_8);
    }

    private CryptoKeyProvider requireKeyProvider() {
        CryptoKeyProvider provider = keyProvider == null ? null : keyProvider.get();
        if (provider == null) {
            throw new SensitiveException("未配置 CryptoKeyProvider，无法使用 ENCRYPT 策略，请检查 modality.crypto 配置");
        }
        return provider;
    }

    private String maskByType(String raw, SensitiveRule rule) {
        if (rule.type() == SensitiveType.EMAIL) {
            return maskEmail(raw, rule);
        }
        return applyMask(raw, rule.prefixKeep(), rule.suffixKeep(), rule.maskChar());
    }

    private String maskEmail(String email, SensitiveRule rule) {
        if (rule.maskChar() == SensitiveConfig.DEFAULT_MASK_CHAR) {
            return StringUtils.maskEmail(email);
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return applyMask(email, 1, 0, rule.maskChar());
        }
        return applyMask(email.substring(0, at), 1, 0, rule.maskChar()) + email.substring(at);
    }

    private String applyMask(String value, int prefixKeep, int suffixKeep, char maskChar) {
        if (maskChar == SensitiveConfig.DEFAULT_MASK_CHAR) {
            return StringUtils.mask(value, prefixKeep, suffixKeep);
        }
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (prefixKeep < 0 || suffixKeep < 0 || prefixKeep + suffixKeep >= length) {
            return String.valueOf(maskChar).repeat(length);
        }
        return value.substring(0, prefixKeep)
                + String.valueOf(maskChar).repeat(length - prefixKeep - suffixKeep)
                + value.substring(length - suffixKeep);
    }
}
