package cn.slainc.redis.config;

import cn.slainc.crypto.key.MapCryptoKeyProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GcmConfigValueCipherTest {

    private static final String KEY_HEX = "hex:0123456789abcdef0123456789abcdef";

    private final GcmConfigValueCipher cipher = new GcmConfigValueCipher(
            MapCryptoKeyProvider.sm4(Map.of("default", KEY_HEX)));

    @Test
    void 国密SM4加解密往返一致() {
        String plain = "数据库口令-模态平台-2026";
        String encrypted = cipher.encrypt(plain);

        assertThat(encrypted).isNotEqualTo(plain);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plain);
    }

    @Test
    void 相同明文两次加密得到不同密文且都能解密() {
        String plain = "同一段明文";
        String first = cipher.encrypt(plain);
        String second = cipher.encrypt(plain);

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(plain);
        assertThat(cipher.decrypt(second)).isEqualTo(plain);
    }

    @Test
    void 空值与非法密文的处理() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
        assertThatThrownBy(() -> cipher.decrypt("不是合法的 Base64 密文"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GcmConfigValueCipher(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CryptoKeyProvider 不能为空");
    }

    @Test
    void 缺少密钥实现时抛出中文异常() {
        ConfigValueCipher unavailable = new UnavailableConfigValueCipher();
        assertThatThrownBy(() -> unavailable.encrypt("明文"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法加密动态配置");
        assertThatThrownBy(() -> unavailable.decrypt("密文"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法解密动态配置");
    }
}
