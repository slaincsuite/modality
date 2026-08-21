package cn.slainc.mybatis.crypto;

import cn.slainc.crypto.digest.DigestUtils;
import cn.slainc.crypto.sm.Sm4Utils;
import cn.slainc.crypto.symmetric.CipherMode;
import cn.slainc.crypto.symmetric.Padding;
import cn.slainc.mybatis.annotation.EncryptField;
import cn.slainc.mybatis.exception.MybatisException;
import cn.slainc.mybatis.properties.MybatisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public class FieldEncryptor {

    private static final Logger log = LoggerFactory.getLogger(FieldEncryptor.class);

    private static final int SM4_KEY_LENGTH = 16;

    private static final int IV_LENGTH = 16;

    private final MybatisProperties properties;

    private final byte[] key;

    public FieldEncryptor(MybatisProperties properties) {
        this.properties = properties;
        this.key = properties.getEncrypt().isEnabled() ? resolveKey(properties.getEncrypt().getSecretKey()) : null;
    }

    public boolean isEnabled() {
        return properties.getEncrypt().isEnabled() && key != null;
    }

    public String encrypt(String plainText) {
        if (!isEnabled() || plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        byte[] cipherText = Sm4Utils.encrypt(CipherMode.CBC, Padding.PKCS7, key, fixedIv(),
                plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(cipherText);
    }

    public String decrypt(String cipherText) {
        if (!isEnabled() || cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            byte[] plain = Sm4Utils.decrypt(CipherMode.CBC, Padding.PKCS7, key, fixedIv(), decoded);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            log.debug("字段解密失败，按原值返回: {}", exception.getMessage());
            return cipherText;
        }
    }

    public static boolean isEncryptedField(Field field) {
        return field.isAnnotationPresent(EncryptField.class);
    }

    // 固定 IV 使密文可确定复现，从而支持按加密列做等值查询
    private byte[] fixedIv() {
        return Arrays.copyOf(DigestUtils.sha256(key), IV_LENGTH);
    }

    private static byte[] resolveKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new MybatisException("启用字段加密时必须配置 modality.mybatis.encrypt.secret-key");
        }
        return Arrays.copyOf(DigestUtils.sha256(secretKey.getBytes(StandardCharsets.UTF_8)), SM4_KEY_LENGTH);
    }
}
