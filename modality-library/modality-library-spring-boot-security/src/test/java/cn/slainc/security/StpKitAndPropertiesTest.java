package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.config.SecurityProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StpKitAndPropertiesTest {

    @BeforeAll
    static void clearStaticAssignments() {
        StpKit.clearAssignments();
    }

    @AfterAll
    static void restoreAssignments() {
        StpKit.assign(new cn.slainc.security.stp.SecurityStpLogic(StpKit.TYPE_ADMIN));
        StpKit.assign(new cn.slainc.security.stp.SecurityStpLogic(StpKit.TYPE_USER));
        StpKit.assign(new cn.slainc.security.stp.SecurityStpLogic(StpKit.TYPE_CLIENT));
    }

    @Test
    @Order(1)
    void uninitializedStpKitThrowsChineseException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, StpKit::admin);
        assertThat(exception.getMessage()).contains("尚未初始化").contains("ADMIN");
    }

    @Test
    @Order(2)
    void ofNullTypeThrowsChineseException() {
        StpKit.assign(new cn.slainc.security.stp.SecurityStpLogic(StpKit.TYPE_USER));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> StpKit.of(null));
        assertThat(exception.getMessage()).isEqualTo("账号体系不能为空");
    }

    @Test
    @Order(3)
    void constantsMatchCoreLoginTypeValues() {
        assertThat(StpKit.TYPE_ADMIN).isEqualTo(LoginType.ADMIN.value());
        assertThat(StpKit.TYPE_USER).isEqualTo(LoginType.USER.value());
        assertThat(StpKit.TYPE_CLIENT).isEqualTo(LoginType.CLIENT.value());
    }

    @Test
    @Order(4)
    void securityPropertiesCarriesDocumentedDefaults() {
        SecurityProperties properties = new SecurityProperties();
        assertThat(properties.getFilter().getExcludePatterns()).contains("/actuator/**", "/v3/api-docs/**",
                "/swagger-ui/**", "/swagger-ui.html", "/webjars/**", "/favicon.ico", "/error", "/captcha/**",
                "/auth/login", "/auth/logout");
        assertThat(properties.getFilter().getProtectedPatterns()).containsExactly("/**");
        assertThat(properties.getFilter().getBlacklistPatterns()).isEmpty();
        assertThat(properties.getTypeHeader()).isEqualTo("X-Login-Type");
        assertThat(properties.getDefaultType()).isEqualTo("USER");
        assertThat(properties.getDeviceIdHeader()).isEqualTo("X-Device-Id");
        assertThat(properties.getPermissionCacheTtlSeconds()).isEqualTo(300);
        assertThat(properties.getOrigin().isEnabled()).isTrue();
        assertThat(properties.getOrigin().getAllowedOrigins()).isEmpty();
        assertThat(properties.getSession().getTimeoutUser()).isEqualTo(2592000);
        assertThat(properties.getDevice().getMaxCountPerType()).isEqualTo(5);
        assertThat(properties.getDevice().getOverflowMode().name()).isEqualTo("KICK_OLDEST");
        assertThat(properties.getDevice().isSingleSessionPerType()).isFalse();
        assertThat(properties.getCookie().getSameSite()).isEqualTo("Lax");
        assertThat(properties.getDao().getRetrySeconds()).isEqualTo(30);
        assertThat(properties.getPassword().getMinLength()).isEqualTo(8);
        assertThat(properties.getPassword().getRequireClasses()).isEqualTo(3);
        assertThat(properties.getPassword().getHistorySize()).isEqualTo(5);
        assertThat(properties.getPassword().getMaxAgeDays()).isEqualTo(90);
        assertThat(properties.getLockout().getMaxFailures()).isEqualTo(5);
        assertThat(properties.getLockout().getLockMinutes()).isEqualTo(30);
    }

    @Test
    @Order(5)
    void propertySettersBind() {
        SecurityProperties properties = new SecurityProperties();
        properties.setTypeHeader("X-Auth-Type");
        properties.setPermissionCacheTtlSeconds(60);
        properties.getFilter().getBlacklistPatterns().add("/bad/**");
        properties.getDevice().setOverflowMode(cn.slainc.security.config.DeviceOverflowMode.REJECT);
        assertThat(properties.getTypeHeader()).isEqualTo("X-Auth-Type");
        assertThat(properties.getPermissionCacheTtlSeconds()).isEqualTo(60);
        assertThat(properties.getFilter().getBlacklistPatterns()).containsExactly("/bad/**");
        assertThat(properties.getDevice().getOverflowMode()).isEqualTo(
                cn.slainc.security.config.DeviceOverflowMode.REJECT);
    }
}
