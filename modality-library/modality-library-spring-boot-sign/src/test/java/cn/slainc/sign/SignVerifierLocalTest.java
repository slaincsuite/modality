package cn.slainc.sign;

import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.sign.composer.SignComposer;
import cn.slainc.sign.composer.SignResult;
import cn.slainc.sign.config.SignProperties;
import cn.slainc.sign.key.SignKeyDefinition;
import cn.slainc.sign.key.SignKeyRegistry;
import cn.slainc.sign.store.MemorySignNonceStore;
import cn.slainc.sign.tool.SignTool;
import cn.slainc.sign.verify.SignVerifyResult;
import cn.slainc.sign.verify.SignVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SignVerifierLocalTest {

    private static final Map<String, List<String>> ORDER_PARAMS =
            Map.of("orderNo", List.of("A001"), "amount", List.of("100"));

    private SignProperties properties;

    private SignVerifier verifier;

    private SignTool signTool;

    private SignKeyDefinition hmacKey;

    private SignKeyDefinition sm3Key;

    private SignKeyDefinition rsaKey;

    private SignKeyDefinition sm2Key;

    @BeforeEach
    void setUp() {
        hmacKey = SignTestSupport.hmacKey("hmac-app", SignAlgorithm.HMAC_SHA256, "hmac-secret-001");
        sm3Key = SignTestSupport.hmacKey("sm3-app", SignAlgorithm.HMAC_SM3, "sm3-secret-001");
        rsaKey = SignTestSupport.rsaKey("rsa-app");
        sm2Key = SignTestSupport.sm2Key("sm2-app");
        SignKeyDefinition disabled = SignTestSupport.hmacKey("disabled-app", SignAlgorithm.HMAC_SHA256, "s");
        disabled.setEnabled(false);
        properties = SignTestSupport.properties(hmacKey, sm3Key, rsaKey, sm2Key, disabled);
        verifier = SignTestSupport.localVerifier(properties, null);
        signTool = new SignTool(new SignComposer(properties));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void hmacSha256V2RoundTripSucceeds() {
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, hmacKey, 2, ORDER_PARAMS);
        SignVerifyResult verified = verifier.verify("hmac-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), ORDER_PARAMS);
        assertThat(verified.success()).as(verified.reason()).isTrue();
        assertThat(verified.appId()).isEqualTo("hmac-app");
    }

    @Test
    void hmacV1RoundTripSucceeds() {
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, hmacKey, 1, ORDER_PARAMS);
        SignVerifyResult verified = verifier.verify("hmac-app", null, 1, result.timestamp(), result.nonce(),
                result.signature(), ORDER_PARAMS);
        assertThat(verified.success()).as(verified.reason()).isTrue();
    }

    @Test
    void hmacSm3RoundTripSucceeds() {
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SM3, sm3Key, 2, ORDER_PARAMS);
        SignVerifyResult verified = verifier.verify("sm3-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), ORDER_PARAMS);
        assertThat(verified.success()).as(verified.reason()).isTrue();
    }

    @Test
    void rsaSha256RoundTripSucceeds() {
        SignResult result = signTool.sign(SignAlgorithm.RSA_SHA256, rsaKey, 2, ORDER_PARAMS);
        SignVerifyResult verified = verifier.verify("rsa-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), ORDER_PARAMS);
        assertThat(verified.success()).as(verified.reason()).isTrue();
    }

    @Test
    void sm2RoundTripSucceeds() {
        SignResult result = signTool.sign(SignAlgorithm.SM2, sm2Key, 2, ORDER_PARAMS);
        SignVerifyResult verified = verifier.verify("sm2-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), ORDER_PARAMS);
        assertThat(verified.success()).as(verified.reason()).isTrue();
    }

    @Test
    void shuffledParametersStillVerify() {
        Map<String, List<String>> shuffled = new LinkedHashMap<>();
        shuffled.put("amount", List.of("100"));
        shuffled.put("orderNo", List.of("A001"));
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, hmacKey, 2, ORDER_PARAMS);
        SignVerifyResult verified = verifier.verify("hmac-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), shuffled);
        assertThat(verified.success()).as(verified.reason()).isTrue();
    }

    @Test
    void tamperedParameterIsRejected() {
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, hmacKey, 2, ORDER_PARAMS);
        Map<String, List<String>> tampered = Map.of("orderNo", List.of("A001"), "amount", List.of("999"));
        SignVerifyResult verified = verifier.verify("hmac-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), tampered);
        assertThat(verified.success()).isFalse();
        assertThat(verified.reason()).contains("签名不匹配");
    }

    @Test
    void expiredTimestampIsRejected() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond() - 400);
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, hmacKey, 2, timestamp,
                SecureRandomUtils.nextHex(8), ORDER_PARAMS, false);
        SignVerifyResult verified = verifier.verify("hmac-app", null, 2, timestamp, result.nonce(),
                result.signature(), ORDER_PARAMS);
        assertThat(verified.success()).isFalse();
        assertThat(verified.reason()).contains("时间戳");
    }

    @Test
    void futureTimestampIsRejected() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond() + 400);
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, hmacKey, 2, timestamp,
                SecureRandomUtils.nextHex(8), ORDER_PARAMS, false);
        SignVerifyResult verified = verifier.verify("hmac-app", null, 2, timestamp, result.nonce(),
                result.signature(), ORDER_PARAMS);
        assertThat(verified.success()).isFalse();
        assertThat(verified.reason()).contains("时间戳");
    }

    @Test
    void milliTimestampIsAutoRecognized() {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, hmacKey, 2, timestamp,
                SecureRandomUtils.nextHex(8), ORDER_PARAMS, false);
        SignVerifyResult verified = verifier.verify("hmac-app", null, 2, timestamp, result.nonce(),
                result.signature(), ORDER_PARAMS);
        assertThat(verified.success()).as(verified.reason()).isTrue();
    }

    @Test
    void windowOverrideExtendsAllowedDrift() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond() - 400);
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, hmacKey, 2, timestamp,
                SecureRandomUtils.nextHex(8), ORDER_PARAMS, false);
        SignVerifyResult verified = verifier.verify("hmac-app", null, 2, timestamp, result.nonce(),
                result.signature(), ORDER_PARAMS, 600, false);
        assertThat(verified.success()).as(verified.reason()).isTrue();
    }

    @Test
    void shortNonceIsRejected() {
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, hmacKey, 2,
                Map.of("a", List.of("1")));
        SignVerifyResult verified = verifier.verify("hmac-app", null, 2, result.timestamp(), "short",
                result.signature(), Map.of("a", List.of("1")));
        assertThat(verified.success()).isFalse();
        assertThat(verified.reason()).contains("nonce");
    }

    @Test
    void nonceReplayIsRejectedInLocalMode() {
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, hmacKey, 2, ORDER_PARAMS);
        SignVerifyResult first = verifier.verify("hmac-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), ORDER_PARAMS);
        SignVerifyResult second = verifier.verify("hmac-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), ORDER_PARAMS);
        assertThat(first.success()).isTrue();
        assertThat(second.success()).isFalse();
        assertThat(second.reason()).contains("重放");
    }

    @Test
    void algorithmDowngradeIsRejectedByWhitelist() {
        properties.setAllowedAlgorithms(List.of(SignAlgorithm.HMAC_SHA256));
        SignResult result = signTool.sign(SignAlgorithm.RSA_SHA256, rsaKey, 2, ORDER_PARAMS);
        SignVerifyResult verified = verifier.verify("rsa-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), ORDER_PARAMS);
        assertThat(verified.success()).isFalse();
        assertThat(verified.reason()).contains("不在允许清单");
    }

    @Test
    void unknownAppIsRejected() {
        SignVerifyResult verified = verifier.verify("ghost", null, 2, "1", "12345678", "sig", Map.of());
        assertThat(verified.success()).isFalse();
        assertThat(verified.reason()).contains("未登记");
    }

    @Test
    void disabledAppIsRejected() {
        SignVerifyResult verified = verifier.verify("disabled-app", null, 2, "1", "12345678", "sig", Map.of());
        assertThat(verified.success()).isFalse();
        assertThat(verified.reason()).contains("已停用");
    }

    @Test
    void blankAppIdIsRejected() {
        SignVerifyResult verified = verifier.verify(" ", null, 2, "1", "12345678", "sig", Map.of());
        assertThat(verified.success()).isFalse();
        assertThat(verified.reason()).contains("应用标识");
    }

    @Test
    void v1WithRsaIsRejected() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        SignVerifyResult verified = verifier.verify("rsa-app", "RSA_SHA256", 1, timestamp,
                "12345678", "sig", Map.of());
        assertThat(verified.success()).isFalse();
        assertThat(verified.reason()).contains("v1");
    }

    @Test
    void blankSignIsRejected() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        SignVerifyResult verified = verifier.verify("hmac-app", null, 2, timestamp, "12345678", "", Map.of());
        assertThat(verified.success()).isFalse();
        assertThat(verified.reason()).contains("缺少签名");
    }

    @Test
    void malformedTimestampIsRejected() {
        SignVerifyResult verified = verifier.verify("hmac-app", null, 2, "not-a-number", "12345678", "sig", Map.of());
        assertThat(verified.success()).isFalse();
        assertThat(verified.reason()).contains("时间戳");
    }

    @Test
    void hexOutputEncodingRoundTrips() {
        properties.setOutputEncoding(SignProperties.OutputEncoding.HEX);
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, hmacKey, 2, ORDER_PARAMS);
        assertThat(result.signature()).matches("[0-9a-f]{64}");
        SignVerifyResult verified = verifier.verify("hmac-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), ORDER_PARAMS);
        assertThat(verified.success()).as(verified.reason()).isTrue();
    }

    @Test
    void invalidSignatureEncodingIsRejected() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        SignVerifyResult verified = verifier.verify("rsa-app", null, 2, timestamp, "12345678", "!!!not-base64!!!",
                Map.of());
        assertThat(verified.success()).isFalse();
        assertThat(verified.reason()).contains("签名不匹配");
    }

    @Test
    void ipWhitelistAllowsConfiguredAddress() {
        SignKeyDefinition ipKey = SignTestSupport.hmacKey("ip-app", SignAlgorithm.HMAC_SHA256, "s");
        ipKey.setAllowedIps(List.of("127.0.0.1"));
        properties.getKeys().add(ipKey);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, ipKey, 2, ORDER_PARAMS);
        SignVerifyResult verified = verifier.verify("ip-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), ORDER_PARAMS);
        assertThat(verified.success()).as(verified.reason()).isTrue();
    }

    @Test
    void ipWhitelistBlocksForeignAddress() {
        SignKeyDefinition cidrKey = SignTestSupport.hmacKey("cidr-app", SignAlgorithm.HMAC_SHA256, "s");
        cidrKey.setAllowedIps(List.of("10.0.0.0/8"));
        properties.getKeys().add(cidrKey);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, cidrKey, 2, ORDER_PARAMS);
        SignVerifyResult verified = verifier.verify("cidr-app", null, 2, result.timestamp(), result.nonce(),
                result.signature(), ORDER_PARAMS);
        assertThat(verified.success()).isFalse();
        assertThat(verified.reason()).contains("白名单");
    }

    @Test
    void failuresAreCountedPerApp() {
        MemorySignNonceStore store = new MemorySignNonceStore();
        SignVerifier countingVerifier = new SignVerifier(new SignKeyRegistry(properties, new SingleProvider<>(null)),
                new SignComposer(properties), properties, store);
        countingVerifier.verify("ghost", null, 2, "1", "12345678", "sig", Map.of());
        countingVerifier.verify("ghost", null, 2, "1", "12345678", "sig", Map.of());
        assertThat(store.failureCount("ghost")).isEqualTo(2);
    }
}
