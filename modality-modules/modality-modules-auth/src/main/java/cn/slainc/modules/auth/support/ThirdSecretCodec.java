package cn.slainc.modules.auth.support;

import cn.slainc.core.exception.BizException;
import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.key.CryptoKeyProvider;
import cn.slainc.crypto.symmetric.AesUtils;

import java.nio.charset.StandardCharsets;

public class ThirdSecretCodec {

    private static final String PREFIX = "third1.";

    private static final int PART_COUNT = 3;

    private final CryptoKeyProvider keyProvider;

    public ThirdSecretCodec(CryptoKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        if (keyProvider == null) {
            throw new BizException("未配置 CryptoKeyProvider，无法安全保存飞书应用密钥");
        }
        byte[] cipher = AesUtils.encryptGcm(keyProvider.currentKeyBytes(),
                plainText.getBytes(StandardCharsets.UTF_8));
        String keyId = Base64Utils.encodeUrlNoPadding(keyProvider.currentKeyId().getBytes(StandardCharsets.UTF_8));
        return PREFIX + keyId + "." + Base64Utils.encodeUrlNoPadding(cipher);
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        if (keyProvider == null) {
            throw new BizException("未配置 CryptoKeyProvider，无法解密飞书应用密钥");
        }
        String[] parts = stored.split("\\.");
        if (parts.length != PART_COUNT) {
            throw new BizException("飞书应用密钥密文格式损坏，请重新填写");
        }
        try {
            String keyId = new String(Base64Utils.decodeUrl(parts[1]), StandardCharsets.UTF_8);
            byte[] payload = Base64Utils.decodeUrl(parts[2]);
            byte[] plain = AesUtils.decryptGcm(keyProvider.key(keyId).getEncoded(), payload);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (CryptoException | IllegalArgumentException e) {
            throw new BizException("飞书应用密钥解密失败，请重新填写后再试");
        }
    }
}
