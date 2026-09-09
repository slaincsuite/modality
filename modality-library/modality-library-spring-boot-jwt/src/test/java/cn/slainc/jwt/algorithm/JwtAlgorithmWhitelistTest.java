package cn.slainc.jwt.algorithm;

import cn.slainc.crypto.jwt.JwtAlgorithm;
import cn.slainc.jwt.verify.JwtVerifyException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAlgorithmWhitelistTest {

    @Test
    void 默认白名单包含_HS256_RS256_ES256() {
        JwtAlgorithmWhitelist whitelist = JwtAlgorithmWhitelist.defaults();
        assertThat(whitelist.isAllowed(JwtAlgorithm.HS256)).isTrue();
        assertThat(whitelist.isAllowed(JwtAlgorithm.RS256)).isTrue();
        assertThat(whitelist.isAllowed(JwtAlgorithm.ES256)).isTrue();
        assertThat(whitelist.allowedAlgorithms()).containsExactlyInAnyOrder(
                JwtAlgorithm.HS256, JwtAlgorithm.RS256, JwtAlgorithm.ES256);
    }

    @Test
    void 默认白名单外的算法被拒绝并给出中文提示() {
        JwtAlgorithmWhitelist whitelist = JwtAlgorithmWhitelist.defaults();
        assertThatThrownBy(() -> whitelist.ensureAllowed(JwtAlgorithm.HS512))
                .isInstanceOf(JwtVerifyException.class)
                .hasMessageContaining("白名单")
                .hasMessageContaining("HS512");
        assertThatThrownBy(() -> whitelist.ensureAllowed(JwtAlgorithm.PS256))
                .hasMessageContaining("PS256");
    }

    @Test
    void 自定义白名单按配置生效() {
        JwtAlgorithmWhitelist whitelist = new JwtAlgorithmWhitelist(List.of(JwtAlgorithm.HS512, JwtAlgorithm.EdDSA));
        assertThat(whitelist.isAllowed(JwtAlgorithm.HS512)).isTrue();
        assertThat(whitelist.isAllowed(JwtAlgorithm.EdDSA)).isTrue();
        assertThatThrownBy(() -> whitelist.ensureAllowed(JwtAlgorithm.HS256))
                .hasMessageContaining("HS256");
    }

    @Test
    void 空白名单构造即拒绝() {
        assertThatThrownBy(() -> new JwtAlgorithmWhitelist(List.of()))
                .isInstanceOf(JwtVerifyException.class)
                .hasMessageContaining("白名单不能为空");
        assertThatThrownBy(() -> new JwtAlgorithmWhitelist(null))
                .hasMessageContaining("白名单不能为空");
    }

    @Test
    void isAllowed对空算法返回假() {
        JwtAlgorithmWhitelist whitelist = JwtAlgorithmWhitelist.defaults();
        assertThat(whitelist.isAllowed(null)).isFalse();
    }

    @Test
    void allowedAlgorithms返回不可变集合() {
        JwtAlgorithmWhitelist whitelist = JwtAlgorithmWhitelist.defaults();
        assertThatThrownBy(() -> whitelist.allowedAlgorithms().add(JwtAlgorithm.HS512))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
