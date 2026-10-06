package cn.slainc.monitor.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignaturesTest {

    @Test
    @DisplayName("钉钉加签与独立 javax.crypto 计算及 openssl 向量一致")
    void dingTalkSignMatchesIndependentComputation() throws Exception {
        assertThat(WebhookSignatures.dingTalkSign(1_700_000_000_000L, "SEC-demo"))
                .isEqualTo("D/xDN+jQpSknk8+PAQJ9+QrB8UoyJMfIh+tA1ge3QFE=");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("another-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getEncoder().encodeToString(
                mac.doFinal("1600000000000\nanother-secret".getBytes(StandardCharsets.UTF_8)));
        assertThat(WebhookSignatures.dingTalkSign(1_600_000_000_000L, "another-secret")).isEqualTo(expected);
    }

    @Test
    @DisplayName("加签 URL 按官方格式拼接 timestamp 与 sign")
    void dingTalkSignedUrlComposition() {
        String url = WebhookSignatures.dingTalkSignedUrl("https://example.com/hook", "SEC-demo", 1_700_000_000_000L);
        assertThat(url).startsWith("https://example.com/hook?timestamp=1700000000000&sign=");
        String withQuery = WebhookSignatures.dingTalkSignedUrl("https://example.com/hook?a=1", "SEC-demo", 1L);
        assertThat(withQuery).startsWith("https://example.com/hook?a=1&timestamp=1&sign=");
    }

    @Test
    @DisplayName("errcode 与 code 非 0 视为失败并附响应内容")
    void verifyPlatformResponseRejectsNonZeroCode() {
        assertThatThrownBy(() -> WebhookSignatures.verifyPlatformResponse("{\"errcode\":310000,\"errmsg\":\"keywords not in content\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Webhook 返回错误码 310000")
                .hasMessageContaining("keywords not in content");
        assertThatThrownBy(() -> WebhookSignatures.verifyPlatformResponse("{\"code\":19021,\"msg\":\"sign match fail\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Webhook 返回错误码 19021");
    }

    @Test
    @DisplayName("成功码、空响应与非 JSON 响应均不抛异常")
    void verifyPlatformResponseAcceptsSuccessAndSkipsNonJson() {
        assertThatCode(() -> WebhookSignatures.verifyPlatformResponse("{\"errcode\":0}")).doesNotThrowAnyException();
        assertThatCode(() -> WebhookSignatures.verifyPlatformResponse("{\"code\":0,\"data\":{}}")).doesNotThrowAnyException();
        assertThatCode(() -> WebhookSignatures.verifyPlatformResponse(null)).doesNotThrowAnyException();
        assertThatCode(() -> WebhookSignatures.verifyPlatformResponse(" ")).doesNotThrowAnyException();
        assertThatCode(() -> WebhookSignatures.verifyPlatformResponse("ok")).doesNotThrowAnyException();
    }
}
