package cn.slainc.monitor.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricNamingTest {

    private final MetricNaming naming = new MetricNaming();

    @Test
    @DisplayName("未带前缀的指标名会自动补上 modality 前缀")
    void shouldAddPrefix() {
        assertThat(naming.normalize("login.success")).isEqualTo("modality.login.success");
        assertThat(naming.normalize("business.order.amount")).isEqualTo("modality.business.order.amount");
    }

    @Test
    @DisplayName("已带前缀的指标名保持原样")
    void shouldKeepPrefix() {
        assertThat(naming.normalize("modality.business.login")).isEqualTo("modality.business.login");
        assertThat(naming.normalize("modality")).isEqualTo("modality");
    }

    @Test
    @DisplayName("下划线、大写、驼峰统一转换成点分小写")
    void shouldNormalizeSeparators() {
        assertThat(naming.normalize("modality_login_success")).isEqualTo("modality.login.success");
        assertThat(naming.normalize("loginSuccess")).isEqualTo("modality.login.success");
        assertThat(naming.normalize("Modality.Login-Success")).isEqualTo("modality.login.success");
        assertThat(naming.normalize("a..b")).isEqualTo("modality.a.b");
        assertThat(naming.normalize(".lead.")).isEqualTo("modality.lead");
    }

    @Test
    @DisplayName("空名字退化为纯前缀")
    void shouldFallbackToPrefix() {
        assertThat(naming.normalize(null)).isEqualTo("modality");
        assertThat(naming.normalize("   ")).isEqualTo("modality");
        assertThat(naming.normalize("...")).isEqualTo("modality");
    }

    @Test
    @DisplayName("支持自定义前缀")
    void shouldSupportCustomPrefix() {
        MetricNaming custom = new MetricNaming("app");
        assertThat(custom.normalize("login.success")).isEqualTo("app.login.success");
        assertThat(custom.normalize("app.login.success")).isEqualTo("app.login.success");
        assertThat(new MetricNaming("My App").prefix()).isEqualTo("myapp");
        assertThat(new MetricNaming(null).prefix()).isEqualTo("modality");
    }

    @Test
    @DisplayName("点分名转 Prometheus 下划线名")
    void shouldConvertToPrometheusName() {
        assertThat(MetricNaming.toPrometheusName("modality.business.login")).isEqualTo("modality_business_login");
        assertThat(MetricNaming.toPrometheusName("modality.system.disk.io.read.bytes"))
                .isEqualTo("modality_system_disk_io_read_bytes");
        assertThat(MetricNaming.toPrometheusName(null)).isNull();
    }

    @Test
    @DisplayName("能判断指标名是否已规范化")
    void shouldDetectCanonicalName() {
        assertThat(naming.isCanonical("modality.business.login")).isTrue();
        assertThat(naming.isCanonical("businessLogin")).isFalse();
        assertThat(naming.isCanonical(null)).isFalse();
    }
}
