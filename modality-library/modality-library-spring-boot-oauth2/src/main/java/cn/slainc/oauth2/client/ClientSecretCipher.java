package cn.slainc.oauth2.client;

import cn.slainc.crypto.hash.Pbkdf2Algorithm;
import cn.slainc.crypto.hash.Pbkdf2Utils;
import cn.slainc.crypto.symmetric.AesUtils;
import cn.slainc.crypto.key.SecretKeyUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ClientSecretCipher {

    public static final String PREFIX = "ENC(";

    public static final String SUFFIX = ")";

    private static final byte[] KDF_SALT = "modality:oauth2:client-secret".getBytes(StandardCharsets.UTF_8);

    private final boolean enabled;

    private final byte[] keyBytes;

    public ClientSecretCipher(String configuredKey) {
        this.enabled = configuredKey != null && !configuredKey.isBlank();
        this.keyBytes = enabled ? deriveKey(configuredKey) : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX) && value.endsWith(SUFFIX);
    }

    public String encrypt(String plainSecret) {
        if (!enabled || plainSecret == null || plainSecret.isBlank() || isEncrypted(plainSecret)) {
            return plainSecret;
        }
        byte[] cipherText = AesUtils.encryptGcm(keyBytes, plainSecret.getBytes(StandardCharsets.UTF_8));
        return PREFIX + Base64.getEncoder().encodeToString(cipherText) + SUFFIX;
    }

    public String decrypt(String storedSecret) {
        if (storedSecret == null || !isEncrypted(storedSecret)) {
            return storedSecret;
        }
        if (!enabled) {
            throw new IllegalStateException("密文需要解密，但未配置 modality.oauth2.client.secret-encryption-key");
        }
        byte[] cipherText = Base64.getDecoder().decode(
                storedSecret.substring(PREFIX.length(), storedSecret.length() - SUFFIX.length()));
        return new String(AesUtils.decryptGcm(keyBytes, cipherText), StandardCharsets.UTF_8);
    }

    private static byte[] deriveKey(String configuredKey) {
        if (configuredKey.startsWith(SecretKeyUtils.HEX_PREFIX) || configuredKey.startsWith(SecretKeyUtils.BASE64_PREFIX)) {
            return SecretKeyUtils.decode(configuredKey, "AES").getEncoded();
        }
        return SecretKeyUtils.aesKey(deriveFromPassphrase(configuredKey)).getEncoded();
    }

    // 口令派生必须与配置字符串一一对应才能解出历史密文，盐只能固定，靠 PBKDF2 迭代抬高暴力破解成本
    private static byte[] deriveFromPassphrase(String configuredKey) {
        return Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA256,
                configuredKey.toCharArray(), KDF_SALT, Pbkdf2Utils.DEFAULT_ITERATIONS,
                Pbkdf2Utils.DEFAULT_KEY_LENGTH);
    }
}
