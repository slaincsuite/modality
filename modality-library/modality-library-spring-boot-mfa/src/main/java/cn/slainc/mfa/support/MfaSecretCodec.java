package cn.slainc.mfa.support;

import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.key.CryptoKeyProvider;
import cn.slainc.crypto.symmetric.AesUtils;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class MfaSecretCodec {

    // 密文格式：mfa1.<base64url(密钥Id)>.<base64url(iv+密文)>，无该前缀的存量值按明文处理
    private static final String CIPHER_PREFIX = "mfa1.";

    private static final int PART_COUNT = 3;

    private static final Logger log = LoggerFactory.getLogger(MfaSecretCodec.class);

    private final CryptoKeyProvider keyProvider;

    public MfaSecretCodec(CryptoKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
        if (keyProvider == null) {
            log.warn("modality.mfa：未提供 CryptoKeyProvider，TOTP 密钥将以明文存储，建议启用 modality.crypto 保护");
        }
    }

    public String encrypt(String plainSecret) {
        if (StringUtils.isEmpty(plainSecret) || keyProvider == null) {
            return plainSecret;
        }
        byte[] cipher = AesUtils.encryptGcm(keyProvider.currentKeyBytes(),
                plainSecret.getBytes(StandardCharsets.UTF_8));
        String keyId = Base64Utils.encodeUrlNoPadding(keyProvider.currentKeyId().getBytes(StandardCharsets.UTF_8));
        return CIPHER_PREFIX + keyId + "." + Base64Utils.encodeUrlNoPadding(cipher);
    }

    public String decrypt(String cipherSecret) {
        if (StringUtils.isEmpty(cipherSecret) || keyProvider == null || !cipherSecret.startsWith(CIPHER_PREFIX)) {
            return cipherSecret;
        }
        String[] parts = cipherSecret.split("\\.");
        if (parts.length != PART_COUNT) {
            return cipherSecret;
        }
        String keyId = new String(Base64Utils.decodeUrl(parts[1]), StandardCharsets.UTF_8);
        byte[] payload = Base64Utils.decodeUrl(parts[2]);
        byte[] plain = AesUtils.decryptGcm(keyProvider.key(keyId).getEncoded(), payload);
        return new String(plain, StandardCharsets.UTF_8);
    }

    public String keyId() {
        return keyProvider == null ? null : keyProvider.currentKeyId();
    }
}
