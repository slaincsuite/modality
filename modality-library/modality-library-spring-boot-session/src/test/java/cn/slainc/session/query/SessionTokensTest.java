package cn.slainc.session.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTokensTest {

    @Test
    @DisplayName("长 token 掩码保留首尾")
    void 长token掩码() {
        String token = "abcdef1234567890xyz";

        String masked = SessionTokens.mask(token);

        assertThat(masked).isEqualTo("abcdef******0xyz");
        assertThat(masked).doesNotContain("1234567890");
    }

    @Test
    @DisplayName("短 token 掩码保留前两位")
    void 短token掩码() {
        assertThat(SessionTokens.mask("abc")).isEqualTo("ab****");
        assertThat(SessionTokens.mask("abcdefgh")).isEqualTo("ab****");
    }

    @Test
    @DisplayName("空 token 原样返回")
    void 空token() {
        assertThat(SessionTokens.mask(null)).isNull();
        assertThat(SessionTokens.mask("")).isEmpty();
    }

    @Test
    @DisplayName("去除存储键前缀得到 token 值")
    void 去除键前缀() {
        assertThat(SessionTokens.strip("satoken:USER:token:abc", "satoken:USER:token:")).isEqualTo("abc");
        assertThat(SessionTokens.strip("abc", "satoken:USER:token:")).isEqualTo("abc");
        assertThat(SessionTokens.strip("satoken:USER:token:abc", null)).isEqualTo("satoken:USER:token:abc");
        assertThat(SessionTokens.strip(null, "satoken:")).isNull();
    }
}
