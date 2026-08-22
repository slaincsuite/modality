package cn.slainc.redis.config;

import cn.slainc.crypto.key.CryptoKeyProvider;
import cn.slainc.crypto.sm.Sm4Utils;
import cn.slainc.crypto.symmetric.AesUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class GcmConfigValueCipher implements ConfigValueCipher {

    private static final int IV_LENGTH = 12;

    private static final String SM4 = "SM4";

    private final CryptoKeyProvider keyProvider;

    private final SecureRandom random = new SecureRandom();

    public GcmConfigValueCipher(CryptoKeyProvider keyProvider) {
        if (keyProvider == null) {
            throw new IllegalArgumentException("CryptoKeyProvider 不能为空");
        }
        this.keyProvider = keyProvider;
    }

    @Override
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        SecretKey key = keyProvider.currentKey();
        byte[] data = plainText.getBytes(StandardCharsets.UTF_8);
        byte[] cipherText;
        if (SM4.equalsIgnoreCase(key.getAlgorithm())) {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            cipherText = Sm4Utils.encryptGcm(key.getEncoded(), iv, data);
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            cipherText = combined;
        } else {
            cipherText = AesUtils.encryptGcm(key.getEncoded(), data);
        }
        return Base64.getEncoder().encodeToString(cipherText);
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        SecretKey key = keyProvider.currentKey();
        byte[] combined = Base64.getDecoder().decode(cipherText);
        byte[] plain;
        if (SM4.equalsIgnoreCase(key.getAlgorithm())) {
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("动态配置密文长度非法，无法解密");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] body = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, body, 0, body.length);
            plain = Sm4Utils.decryptGcm(key.getEncoded(), iv, body);
        } else {
            plain = AesUtils.decryptGcm(key.getEncoded(), combined);
        }
        return new String(plain, StandardCharsets.UTF_8);
    }
}
