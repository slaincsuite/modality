package cn.slainc.datasource.crypto;

import cn.slainc.datasource.exception.DataSourceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourcePasswordEncryptorTest {

    @Test
    @DisplayName("启用加密但未配置密钥时快速失败")
    void enabledWithoutKeyFailsFast() {
        DataSourceException exception = assertThrows(DataSourceException.class,
                () -> new DataSourcePasswordEncryptor(true, " ", "modality"));

        assertTrue(exception.getMessage().contains("modality.datasource.encrypt.key"), exception.getMessage());
    }

    @Test
    @DisplayName("未启用加密时明文原样返回")
    void disabledKeepsPlainText() {
        DataSourcePasswordEncryptor encryptor = new DataSourcePasswordEncryptor(false, null, "modality");

        assertFalse(encryptor.isEnabled());
        assertEquals("plain-pass", encryptor.encrypt("plain-pass"));
        assertEquals("plain-pass", encryptor.decrypt("plain-pass"));
    }

    @Test
    @DisplayName("启用加密后密文可解回明文且带 ENC 前缀")
    void enabledRoundTripsCipherText() {
        DataSourcePasswordEncryptor encryptor = new DataSourcePasswordEncryptor(true, "modality-test-kek", "test");

        String encrypted = encryptor.encrypt("s3cret-pass");

        assertTrue(encryptor.isEnabled());
        assertTrue(encrypted.startsWith(DataSourcePasswordEncryptor.PREFIX));
        assertFalse(encrypted.contains("s3cret-pass"));
        assertEquals("s3cret-pass", encryptor.decrypt(encrypted));
        assertEquals(encrypted, encryptor.encrypt(encrypted));
    }

    @Test
    @DisplayName("未启用加密时遇到密文明确报错")
    void disabledRejectsCipherText() {
        DataSourcePasswordEncryptor encryptor = new DataSourcePasswordEncryptor(false, null, "modality");

        DataSourceException exception = assertThrows(DataSourceException.class,
                () -> encryptor.decrypt("ENC(modality:AES:AAAA:BBBB:CCCC)"));

        assertEquals("数据源密码已加密，但未配置解密密钥", exception.getMessage());
    }
}
