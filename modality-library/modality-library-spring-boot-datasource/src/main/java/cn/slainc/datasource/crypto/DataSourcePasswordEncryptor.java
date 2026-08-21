package cn.slainc.datasource.crypto;

import cn.slainc.crypto.digest.DigestUtils;
import cn.slainc.crypto.key.EncryptedData;
import cn.slainc.crypto.key.EnvelopeEncryption;
import cn.slainc.crypto.key.SecretKeyUtils;
import cn.slainc.datasource.exception.DataSourceException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class DataSourcePasswordEncryptor {

    public static final String PREFIX = "ENC(";

    public static final String SUFFIX = ")";

    private final EnvelopeEncryption envelopeEncryption;

    private final boolean enabled;

    public DataSourcePasswordEncryptor(boolean enabled, String secretKey, String keyId) {
        this.enabled = enabled;
        this.envelopeEncryption = enabled ? new EnvelopeEncryption(resolveKey(secretKey), keyId) : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX) && value.endsWith(SUFFIX);
    }

    public String encrypt(String plainText) {
        if (!enabled || plainText == null || plainText.isEmpty() || isEncrypted(plainText)) {
            return plainText;
        }
        EncryptedData data = envelopeEncryption.encrypt(plainText.getBytes(StandardCharsets.UTF_8));
        return PREFIX + data.keyId() + ':' + data.keyAlgorithm() + ':'
                + Base64.getEncoder().encodeToString(data.wrappedKey()) + ':'
                + Base64.getEncoder().encodeToString(data.iv()) + ':'
                + Base64.getEncoder().encodeToString(data.cipherText()) + SUFFIX;
    }

    public String decrypt(String storedValue) {
        if (!isEncrypted(storedValue)) {
            return storedValue;
        }
        if (!enabled) {
            throw new DataSourceException("数据源密码已加密，但未配置解密密钥");
        }
        String body = storedValue.substring(PREFIX.length(), storedValue.length() - SUFFIX.length());
        String[] parts = body.split(":");
        if (parts.length != 5) {
            throw new DataSourceException("数据源密码密文格式不合法");
        }
        try {
            EncryptedData data = new EncryptedData(parts[0], parts[1],
                    Base64.getDecoder().decode(parts[2]),
                    Base64.getDecoder().decode(parts[3]),
                    Base64.getDecoder().decode(parts[4]));
            return new String(envelopeEncryption.decrypt(data), StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw new DataSourceException("数据源密码解密失败", exception);
        }
    }

    private static SecretKey resolveKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new DataSourceException("启用数据源密码加密时必须配置密钥");
        }
        String value = secretKey.trim();
        if (value.startsWith(SecretKeyUtils.HEX_PREFIX) || value.startsWith(SecretKeyUtils.BASE64_PREFIX)) {
            return SecretKeyUtils.decode(value, "AES");
        }
        return SecretKeyUtils.aesKey(DigestUtils.sha256(value.getBytes(StandardCharsets.UTF_8)));
    }
}
