package cn.slainc.modules.upms.service;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.security.UpmsAccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPasswordServiceTest extends UpmsIntegrationTestBase {

    @Autowired
    private UserPasswordService passwordService;

    @Autowired
    private UserService userService;

    @Autowired
    private UpmsAccountAuthProvider authProvider;

    @Test
    void adminResetEnforcesStrengthAndHistory() {
        UserRecord user = userService.create("pw-user", null, "Old#12345", null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        assertThatThrownBy(() -> passwordService.resetByAdmin(user.getId(), "short"))
                .isInstanceOf(BizException.class);

        passwordService.resetByAdmin(user.getId(), "New#67890");
        PasswordAccount account = authProvider.findAccount("pw-user", LoginType.TENANT_USER);
        assertThat(account.mustChangePassword()).isTrue();
        assertThat(cn.slainc.crypto.hash.BCryptUtils.verifyQuietly("New#67890", account.passwordHash())).isTrue();

        assertThatThrownBy(() -> passwordService.resetByAdmin(user.getId(), "New#67890"))
                .isInstanceOf(BizException.class).hasMessageContaining("历史密码");
    }

    @Test
    void selfChangeVerifiesOldPassword() {
        UserRecord user = userService.create("pw-self", null, "Origin#123", null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        assertThatThrownBy(() -> passwordService.changeBySelf(user.getId(), LoginType.TENANT_USER, "wrong",
                "Fresh#456")).isInstanceOf(Exception.class);
        passwordService.changeBySelf(user.getId(), LoginType.TENANT_USER, "Origin#123", "Fresh#456");
        assertThat(cn.slainc.crypto.hash.BCryptUtils.verifyQuietly("Fresh#456",
                authProvider.findAccount("pw-self", LoginType.TENANT_USER).passwordHash())).isTrue();
        assertThat(passwordService.mustChangePassword(user.getId(), LoginType.TENANT_USER)).isFalse();
    }
}
