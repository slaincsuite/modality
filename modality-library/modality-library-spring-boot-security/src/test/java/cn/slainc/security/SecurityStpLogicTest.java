package cn.slainc.security;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.config.SecurityKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityStpLogicTest extends SecurityAcceptanceTestBase {

    @Autowired
    cn.slainc.security.stp.SecurityStpLogic userLogic;

    @Test
    void loginWrapperCreatesSessionWithToken() {
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.setMockContext();
        try {
            userLogic.login(4321L, new SaLoginParameter().setIsShare(false));
            // Sa-Token 把 loginId 以字符串写入存储，读回必然是字符串
            assertThat(String.valueOf(userLogic.getLoginId())).isEqualTo("4321");
            userLogic.logout();
        } finally {
            cn.dev33.satoken.context.mock.SaTokenContextMockUtil.clearContext();
        }
    }

    @Test
    void logoutAndClearRemovesAuxiliaryKeys() {
        redis.opsForValue().set(SecurityKeys.lastIpKey("TENANT_USER", 5555L), "1.1.1.1", java.time.Duration.ofSeconds(60));
        redis.opsForValue().set(SecurityKeys.lockFailKey("TENANT_USER", 5555L), "2", java.time.Duration.ofSeconds(60));
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.setMockContext();
        try {
            userLogic.login(5555L, new SaLoginParameter().setIsShare(false));
            userLogic.logoutAndClear(5555L);
            assertThat(redis.opsForValue().get(SecurityKeys.lastIpKey("TENANT_USER", 5555L))).isNull();
            assertThat(redis.opsForValue().get(SecurityKeys.lockFailKey("TENANT_USER", 5555L))).isNull();
        } finally {
            cn.dev33.satoken.context.mock.SaTokenContextMockUtil.clearContext();
        }
    }

    @Test
    void loginTypeValueMatchesCoreEnum() {
        assertThat(userLogic.getLoginType()).isEqualTo(LoginType.TENANT_USER.value());
        assertThat(StpKit.platformAdmin().getLoginType()).isEqualTo(LoginType.PLATFORM_ADMIN.value());
        assertThat(StpKit.app().getLoginType()).isEqualTo(LoginType.APP.value());
    }
}
