package cn.slainc.sign;

import cn.slainc.sign.composer.SignComposer;
import cn.slainc.sign.config.SignProperties;
import cn.slainc.sign.key.SignKeyDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignComposerTest {

    private final SignComposer composer = new SignComposer(new SignProperties());

    @Test
    void parametersAreSortedByKeyAndArraysExpanded() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("c", List.of("y", "x"));
        params.put("a", List.of("1"));
        params.put("b", List.of("2"));
        assertThat(composer.compose(1, "app", "123", "n1", params, false)).isEqualTo("a=1&b=2&c=y&c=x");
    }

    @Test
    void insertionOrderDoesNotChangeSignature() {
        Map<String, List<String>> first = new LinkedHashMap<>();
        first.put("amount", List.of("100"));
        first.put("orderNo", List.of("A001"));
        Map<String, List<String>> second = new LinkedHashMap<>();
        second.put("orderNo", List.of("A001"));
        second.put("amount", List.of("100"));
        SignKeyDefinition key = SignTestSupport.hmacKey("app", SignAlgorithm.HMAC_SHA256, "secret-1");
        String canonicalFirst = composer.compose(2, "app", "123", "n1", first, false);
        String canonicalSecond = composer.compose(2, "app", "123", "n1", second, false);
        assertThat(canonicalFirst).isEqualTo(canonicalSecond);
        assertThat(composer.sign(2, SignAlgorithm.HMAC_SHA256, key, canonicalFirst))
                .isEqualTo(composer.sign(2, SignAlgorithm.HMAC_SHA256, key, canonicalSecond));
    }

    @Test
    void signParameterItselfIsExcluded() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("sign", List.of("forged"));
        params.put("a", List.of("1"));
        assertThat(composer.compose(1, "app", "123", "n1", params, false)).isEqualTo("a=1");
    }

    @Test
    void emptyValuesAreSkippedUnlessIncluded() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("a", List.of(""));
        params.put("b", List.of(""));
        params.put("c", List.of("3"));
        assertThat(composer.compose(1, "app", "123", "n1", params, false)).isEqualTo("c=3");
        assertThat(composer.compose(1, "app", "123", "n1", params, true)).isEqualTo("a=&b=&c=3");
    }

    @Test
    void nullValueListIsSkippedUnlessIncluded() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("a", null);
        params.put("b", List.of("2"));
        assertThat(composer.compose(1, "app", "123", "n1", params, false)).isEqualTo("b=2");
        assertThat(composer.compose(1, "app", "123", "n1", params, true)).isEqualTo("a=&b=2");
    }

    @Test
    void v2CanonicalPutsContextBeforeParameters() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("a", List.of("1"));
        assertThat(composer.compose(2, "app", "123", "n1", params, false))
                .isEqualTo("appId=app&nonce=n1&timestamp=123&a=1");
        assertThat(composer.compose(2, "app", "123", "n1", Map.of(), false))
                .isEqualTo("appId=app&nonce=n1&timestamp=123");
    }

    @Test
    void v2ExcludesReservedContextParameters() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("appId", List.of("spoof"));
        params.put("timestamp", List.of("999"));
        params.put("nonce", List.of("spoof"));
        params.put("sign", List.of("spoof"));
        params.put("a", List.of("1"));
        assertThat(composer.compose(2, "app", "123", "n1", params, false))
                .isEqualTo("appId=app&nonce=n1&timestamp=123&a=1");
    }

    @Test
    void v1SignatureIsLowercaseHexAndDeterministic() {
        SignKeyDefinition key = SignTestSupport.hmacKey("app", SignAlgorithm.HMAC_SHA256, "secret-1");
        String canonical = composer.compose(1, "app", "123", "n1", Map.of("a", List.of("1")), false);
        String signature = composer.sign(1, SignAlgorithm.HMAC_SHA256, key, canonical);
        assertThat(signature).matches("[0-9a-f]{64}");
        assertThat(composer.sign(1, SignAlgorithm.HMAC_SHA256, key, canonical)).isEqualTo(signature);
    }

    @Test
    void v1RejectsNonHmacAlgorithm() {
        SignKeyDefinition key = SignTestSupport.rsaKey("app");
        assertThatThrownBy(() -> composer.sign(1, SignAlgorithm.RSA_SHA256, key, "a=1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v1 签名仅支持 HMAC 算法");
    }

    @Test
    void fromNameIsTolerantToCaseAndDash() {
        assertThat(SignAlgorithm.fromName("hmac_sha256")).isEqualTo(SignAlgorithm.HMAC_SHA256);
        assertThat(SignAlgorithm.fromName("HMAC-SM3")).isEqualTo(SignAlgorithm.HMAC_SM3);
        assertThat(SignAlgorithm.fromName("rsa-sha256")).isEqualTo(SignAlgorithm.RSA_SHA256);
        assertThat(SignAlgorithm.fromName("sm2")).isEqualTo(SignAlgorithm.SM2);
        assertThat(SignAlgorithm.fromName("md5")).isNull();
        assertThat(SignAlgorithm.fromName("")).isNull();
        assertThat(SignAlgorithm.fromName(null)).isNull();
    }
}
