package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.config.SecurityProperties;
import cn.slainc.security.stp.SecurityStpLogic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StpKitAndPropertiesTest {

    private static final Map<LoginType, SecurityStpLogic> SNAPSHOT = new EnumMap<>(LoginType.class);

    @BeforeAll
    static void clearStaticAssignments() {
        for (LoginType type : LoginType.values()) {
            SecurityStpLogic logic = current(type);
            if (logic != null) {
                SNAPSHOT.put(type, logic);
            }
        }
        StpKit.clearAssignments();
    }

    @AfterAll
    static void restoreAssignments() {
        StpKit.clearAssignments();
        SNAPSHOT.values().forEach(StpKit::assign);
    }

    private static SecurityStpLogic current(LoginType type) {
        try {
            return StpKit.of(type);
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    @Test
    @Order(1)
    void uninitializedStpKitThrowsChineseException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, StpKit::platformAdmin);
        assertThat(exception.getMessage()).contains("尚未初始化").contains("PLATFORM_ADMIN");
    }

    @Test
    @Order(2)
    void ofNullTypeThrowsChineseException() {
        StpKit.assign(new SecurityStpLogic(StpKit.TYPE_TENANT_USER));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> StpKit.of(null));
        assertThat(exception.getMessage()).isEqualTo("账号体系不能为空");
    }

    @Test
    @Order(3)
    void constantsMatchCoreLoginTypeValues() {
        assertThat(StpKit.TYPE_PLATFORM_ADMIN).isEqualTo(LoginType.PLATFORM_ADMIN.value());
        assertThat(StpKit.TYPE_TENANT_USER).isEqualTo(LoginType.TENANT_USER.value());
        assertThat(StpKit.TYPE_APP).isEqualTo(LoginType.APP.value());
    }

    @Test
    @Order(4)
    void securityPropertiesCarriesDocumentedDefaults() {
        SecurityProperties properties = new SecurityProperties();
        assertThat(properties.getFilter().getExcludePatterns()).contains("/actuator/health", "/actuator/info",
                "/favicon.ico", "/error", "/captcha/**", "/auth/login", "/auth/refresh", "/auth/logout",
                "/auth/token/refresh", "/oauth2/jwks", "/oauth2/token", "/oauth2/userinfo", "/sso/**")
                .doesNotContain("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/webjars/**");
        assertThat(properties.getFilter().getProtectedPatterns()).containsExactly("/**");
        assertThat(properties.getFilter().getBlacklistPatterns()).isEmpty();
        assertThat(properties.getTypeHeader()).isEqualTo("X-Login-Type");
        assertThat(properties.getDefaultType()).isEqualTo("TENANT_USER");
        assertThat(properties.getDeviceIdHeader()).isEqualTo("X-Device-Id");
        assertThat(properties.getPermissionCacheTtlSeconds()).isEqualTo(300);
        assertThat(properties.getOrigin().isEnabled()).isTrue();
        assertThat(properties.getOrigin().getAllowedOrigins()).isEmpty();
        assertThat(properties.getSession().getTimeoutTenantUser()).isEqualTo(7200);
        assertThat(properties.getSession().getActiveTimeout()).isEqualTo(1800);
        assertThat(properties.getCookie().isSecure()).isTrue();
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
