package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginTypesResolverTest {

    @Test
    void resolvesKnownValuesIgnoringCase() {
        assertThat(LoginTypes.resolve("ADMIN")).isEqualTo(LoginType.ADMIN);
        assertThat(LoginTypes.resolve("user")).isEqualTo(LoginType.USER);
        assertThat(LoginTypes.resolve("Client")).isEqualTo(LoginType.CLIENT);
    }

    @Test
    void unknownValueThrowsChineseException() {
        BizException exception = assertThrows(BizException.class, () -> LoginTypes.resolve("GUEST"));
        assertThat(exception.getMessage()).contains("未知的账号体系").contains("GUEST");
    }

    @Test
    void resolveOrDefaultFallsBackToConfiguredDefault() {
        assertThat(LoginTypes.resolveOrDefault(null, "CLIENT")).isEqualTo(LoginType.CLIENT);
        assertThat(LoginTypes.resolveOrDefault("bogus", "ADMIN")).isEqualTo(LoginType.ADMIN);
        assertThat(LoginTypes.resolveOrDefault("USER", "ADMIN")).isEqualTo(LoginType.USER);
    }

    @Test
    void resolveOrDefaultFallsBackToUserWhenDefaultUnknown() {
        assertThat(LoginTypes.resolveOrDefault(null, "whatever")).isEqualTo(LoginType.USER);
    }
}
