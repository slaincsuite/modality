package cn.slainc.services.livemonitor.support;

import cn.slainc.core.exception.BizException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamUrlGuardTest {

    private final StreamUrlGuard guard = new StreamUrlGuard();

    @Test
    void shouldAllowPublicLiteralAddress() {
        assertThat(guard.validate("https://8.8.8.8/live/stream.m3u8"))
                .hasToString("https://8.8.8.8/live/stream.m3u8");
    }

    @Test
    void shouldRejectInternalLoopbackLinkLocalAndObfuscatedTargets() {
        List<String> targets = List.of(
                "http://127.0.0.1/live",
                "http://127.1/live",
                "http://2130706433/live",
                "http://0x7f000001/live",
                "http://[::1]/live",
                "http://[::ffff:192.168.1.1]/live",
                "http://10.0.0.8/live",
                "http://172.16.1.1/live",
                "http://192.168.1.1/live",
                "http://169.254.169.254/latest/meta-data",
                "http://100.64.0.1/live",
                "http://192.0.2.10/live",
                "http://localhost/live",
                "http://stream.local/live",
                "http://metadata.internal/live");

        for (String target : targets) {
            assertThatThrownBy(() -> guard.validate(target))
                    .as("地址 %s 应被 SSRF 守卫拒绝", target)
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("内网");
        }
    }

    @Test
    void shouldRejectResolvedInternalAddress() throws Exception {
        StreamUrlGuard resolvingGuard = new StreamUrlGuard(host ->
                new InetAddress[]{InetAddress.getByName("10.0.0.7")});

        assertThatThrownBy(() -> resolvingGuard.validate("https://stream.example/live"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("内网");
    }

    @Test
    void shouldAllowResolvedPublicAddressAndRejectResolutionFailure() throws Exception {
        StreamUrlGuard publicGuard = new StreamUrlGuard(host ->
                new InetAddress[]{InetAddress.getByName("8.8.4.4")});
        assertThat(publicGuard.validate("https://stream.example/live")).isNotNull();

        StreamUrlGuard brokenGuard = new StreamUrlGuard(host -> {
            throw new UnknownHostException(host);
        });
        assertThatThrownBy(() -> brokenGuard.validate("https://missing.example/live"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法解析");
    }

    @Test
    void shouldRejectUnsupportedSchemeAndMalformedUrl() {
        assertThatThrownBy(() -> guard.validate("ftp://8.8.8.8/live"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("http/https");
        assertThatThrownBy(() -> guard.validate("not a url"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是合法的 URL");
        assertThatThrownBy(() -> guard.validate("http://user:pass@8.8.8.8/live"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户信息");
    }
}
