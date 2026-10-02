package cn.slainc.modules.infra.store;

import cn.slainc.crypto.symmetric.AesUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

// 配置历史的值在库内以 AES-GCM 落盘：加密配置的明文与普通配置的值都不允许在历史表裸存
public class ConfigHistoryCipher {

    private static final String PREFIX = "ENC:";

    private static final String DEFAULT_MATERIAL = "modality-infra-config-history-default";

    private final byte[] key;

    public ConfigHistoryCipher(String material) {
        String effective = material == null || material.isBlank() ? DEFAULT_MATERIAL : material;
        this.key = sha256(effective);
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        byte[] cipherText = AesUtils.encryptGcm(key, plain.getBytes(StandardCharsets.UTF_8));
        return PREFIX + Base64.getEncoder().encodeToString(cipherText);
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        byte[] cipherText = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        return new String(AesUtils.decryptGcm(key, cipherText), StandardCharsets.UTF_8);
    }

    private static byte[] sha256(String material) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("初始化配置历史加密密钥失败", exception);
        }
    }
}
