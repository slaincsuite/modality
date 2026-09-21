package cn.slainc.session.device;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTypeNormalizerTest {

    @Test
    @DisplayName("网页与桌面端归一为 PC")
    void normalizedToPc() {
        assertThat(DeviceTypeNormalizer.normalize("web")).isEqualTo("PC");
        assertThat(DeviceTypeNormalizer.normalize("PC")).isEqualTo("PC");
        assertThat(DeviceTypeNormalizer.normalize("desktop")).isEqualTo("PC");
        assertThat(DeviceTypeNormalizer.normalize("Windows")).isEqualTo("PC");
    }

    @Test
    @DisplayName("移动端归一为 MOBILE")
    void normalizedToMobile() {
        assertThat(DeviceTypeNormalizer.normalize("app-android")).isEqualTo("MOBILE");
        assertThat(DeviceTypeNormalizer.normalize("app-ios")).isEqualTo("MOBILE");
        assertThat(DeviceTypeNormalizer.normalize("phone")).isEqualTo("MOBILE");
        assertThat(DeviceTypeNormalizer.normalize("MOBILE")).isEqualTo("MOBILE");
    }

    @Test
    @DisplayName("平板归一为 TABLET")
    void normalizedToTablet() {
        assertThat(DeviceTypeNormalizer.normalize("ipad")).isEqualTo("TABLET");
        assertThat(DeviceTypeNormalizer.normalize("tablet")).isEqualTo("TABLET");
    }

    @Test
    @DisplayName("未知设备类型归一为 UNKNOWN")
    void unknownNormalized() {
        assertThat(DeviceTypeNormalizer.normalize("default")).isEqualTo("UNKNOWN");
        assertThat(DeviceTypeNormalizer.normalize("")).isEqualTo("UNKNOWN");
        assertThat(DeviceTypeNormalizer.normalize(null)).isEqualTo("UNKNOWN");
        assertThat(DeviceTypeNormalizer.normalize("未知终端")).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("设备类型缺失时用 User-Agent 兜底")
    void fallsBackToUserAgent() {
        String iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15";
        String windows = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0";

        assertThat(DeviceTypeNormalizer.normalize(null, iphone)).isEqualTo("MOBILE");
        assertThat(DeviceTypeNormalizer.normalize("", windows)).isEqualTo("PC");
    }

    @Test
    @DisplayName("匹配支持原始类型与归一类别")
    void matchesRawAndCategory() {
        assertThat(DeviceTypeNormalizer.matches("app-android", null, "app-android")).isTrue();
        assertThat(DeviceTypeNormalizer.matches("app-android", null, "MOBILE")).isTrue();
        assertThat(DeviceTypeNormalizer.matches("app-android", null, "PC")).isFalse();
        assertThat(DeviceTypeNormalizer.matches("default", null, "UNKNOWN")).isTrue();
        assertThat(DeviceTypeNormalizer.matches("web", null, " ")).isTrue();
    }

    @Test
    @DisplayName("机器人与未知 UA 归一为 UNKNOWN")
    void botNormalizedToUnknown() {
        String bot = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

        assertThat(DeviceTypeNormalizer.normalize(null, bot)).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("设备类型匹配大小写不敏感")
    void caseInsensitive() {
        assertThat(DeviceTypeNormalizer.normalize("Web")).isEqualTo("PC");
        assertThat(DeviceTypeNormalizer.matches("Web", null, "pc")).isTrue();
    }
}
