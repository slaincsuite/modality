package cn.slainc.monitor.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlValidatorTest {

    @Test
    @DisplayName("仅允许 http/https 且必须有主机名")
    void rejectsNonHttpSchemes() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("ftp://example.com/hook"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("仅支持 http/https");
        assertThatThrownBy(() -> WebhookUrlValidator.validate("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("仅支持 http/https");
        assertThatThrownBy(() -> WebhookUrlValidator.validate("example.com/hook"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("仅支持 http/https");
        assertThatThrownBy(() -> WebhookUrlValidator.validate("http:///hook"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("缺少主机名");
        assertThatThrownBy(() -> WebhookUrlValidator.validate(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("拒绝环回、私网、链路本地、组播与保留地址")
    void rejectsLoopbackPrivateAndReservedLiterals() {
        assertRejected("http://127.0.0.1:8080/hook");
        assertRejected("http://127.1.2.3/hook");
        assertRejected("http://10.0.0.5/hook");
        assertRejected("http://172.16.3.4/hook");
        assertRejected("http://192.168.1.1/hook");
        assertRejected("http://169.254.169.254/latest/meta-data");
        assertRejected("http://100.64.0.1/hook");
        assertRejected("http://198.18.0.1/hook");
        assertRejected("http://224.0.0.1/hook");
        assertRejected("http://0.0.0.0/hook");
    }

    @Test
    @DisplayName("十进制/八进制/十六进制 IP 绕过同样被拒")
    void rejectsNumericIpBypasses() {
        assertRejected("http://2130706433/hook");
        assertRejected("http://0177.0.0.1/hook");
        assertRejected("http://0x7f.0.0.1/hook");
        assertRejected("http://0x7f000001/hook");
        assertRejected("http://127.1/hook");
        assertRejected("http://3232235777/hook");
    }

    @Test
    @DisplayName("IPv6 与 IPv4 映射地址同样被拒")
    void rejectsIpv6InternalAddresses() {
        assertRejected("http://[::1]/hook");
        assertRejected("http://[fe80::1]/hook");
        assertRejected("http://[fc00::1]/hook");
        assertRejected("http://[ff02::1]/hook");
        assertRejected("http://[::ffff:127.0.0.1]/hook");
        assertRejected("http://[::ffff:10.0.0.1]/hook");
        assertThatThrownBy(() -> WebhookUrlValidator.validate("http://[zz::1]/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Webhook 地址");
    }

    @Test
    @DisplayName("内网主机名与解析到内网地址的域名被拒")
    void rejectsInternalHostNames() {
        assertRejected("http://localhost/hook");
        assertRejected("http://api.localhost/hook");
        assertRejected("http://alert.internal/hook");
        assertRejected("http://receiver.lan/hook");
        assertRejected("http://metadata.home.arpa/hook");
    }

    @Test
    @DisplayName("公网字面量与显式放行内网时可校验通过")
    void allowsPublicLiteralAndExplicitInternalOptIn() {
        assertThatCode(() -> WebhookUrlValidator.validate("https://93.184.216.34/hook")).doesNotThrowAnyException();
        assertThatCode(() -> WebhookUrlValidator.validate("http://10.0.0.5:9000/hook", true))
                .doesNotThrowAnyException();
        assertThatCode(() -> WebhookUrlValidator.validate("http://localhost:9000/hook", true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("地址判定覆盖保留网段")
    void internalAddressDetectionCoversReservedRanges() throws UnknownHostException {
        assertThat(WebhookUrlValidator.isInternalAddress(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(WebhookUrlValidator.isInternalAddress(InetAddress.getByName("192.0.0.8"))).isTrue();
        assertThat(WebhookUrlValidator.isInternalAddress(InetAddress.getByName("::ffff:192.168.0.1"))).isTrue();
        assertThat(WebhookUrlValidator.isInternalAddress(InetAddress.getByName("2001:4860:4860::8888"))).isFalse();
    }

    @Test
    @DisplayName("WebhookDefinition 与 HttpWebhookTransport 都执行校验")
    void definitionAndTransportEnforceValidation() {
        WebhookDefinition definition = new WebhookDefinition();
        definition.setName("内网通道");
        definition.setUrl("http://169.254.169.254/latest/meta-data");
        assertThatThrownBy(definition::validate).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("配置不合法");

        WebhookDefinition allowed = new WebhookDefinition();
        allowed.setName("自建接收端");
        allowed.setUrl("http://10.0.0.9:8080/hook");
        allowed.validate(true);
        assertThat(allowed.getUrl()).isEqualTo("http://10.0.0.9:8080/hook");

        HttpWebhookTransport transport = new HttpWebhookTransport(null, null);
        assertThatThrownBy(() -> transport.post("http://127.0.0.1:9999/hook", "{}", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("内网");
    }

    private void assertRejected(String url) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(url))
                .as("应拒绝 %s", url)
                .isInstanceOf(IllegalArgumentException.class);
    }
}
