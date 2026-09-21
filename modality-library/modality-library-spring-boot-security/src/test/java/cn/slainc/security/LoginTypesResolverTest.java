package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginTypesResolverTest {

    @Test
    void resolvesKnownValuesIgnoringCase() {
        assertThat(LoginTypes.resolve("PLATFORM_ADMIN")).isEqualTo(LoginType.PLATFORM_ADMIN);
        assertThat(LoginTypes.resolve("tenant_user")).isEqualTo(LoginType.TENANT_USER);
        assertThat(LoginTypes.resolve("app")).isEqualTo(LoginType.APP);
    }

    @Test
    void unknownValueThrowsChineseException() {
        BizException exception = assertThrows(BizException.class, () -> LoginTypes.resolve("bogus"));
        assertThat(exception.getMessage()).contains("未知的账号体系").contains("bogus");
    }

    @Test
    void resolveOrDefaultFallsBackToConfiguredDefault() {
        assertThat(LoginTypes.resolveOrDefault(null, "APP")).isEqualTo(LoginType.APP);
        assertThat(LoginTypes.resolveOrDefault("bogus", "PLATFORM_ADMIN")).isEqualTo(LoginType.PLATFORM_ADMIN);
        assertThat(LoginTypes.resolveOrDefault("TENANT_USER", "PLATFORM_ADMIN")).isEqualTo(LoginType.TENANT_USER);
    }

    @Test
    void resolveOrDefaultFallsBackToUserWhenDefaultUnknown() {
        assertThat(LoginTypes.resolveOrDefault(null, "whatever")).isEqualTo(LoginType.TENANT_USER);
    }
}
