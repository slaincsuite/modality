package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.config.SecurityProperties;
import cn.slainc.security.spi.ConfigAccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigAccountAuthProviderTest {

    private SecurityProperties propertiesWithAccounts() {
        SecurityProperties properties = new SecurityProperties();
        SecurityProperties.AccountItem admin = new SecurityProperties.AccountItem();
        admin.setUsername("cfg-admin");
        admin.setPasswordBcrypt("$2a$10$abcdefghijklmnopqrstuv");
        admin.setUserId(5001L);
        admin.setType("PLATFORM_ADMIN");
        admin.setEnabled(true);
        admin.setMustChangePassword(true);
        properties.getAccounts().add(admin);

        SecurityProperties.AccountItem disabled = new SecurityProperties.AccountItem();
        disabled.setUsername("cfg-disabled");
        disabled.setPasswordBcrypt("$2a$10$abcdefghijklmnopqrstuv");
        disabled.setUserId(5002L);
        disabled.setType("TENANT_USER");
        disabled.setEnabled(false);
        properties.getAccounts().add(disabled);
        return properties;
    }

    @Test
    void findsAccountByUsernameAndType() {
        ConfigAccountAuthProvider provider = new ConfigAccountAuthProvider(propertiesWithAccounts());
        PasswordAccount account = provider.findAccount("cfg-admin", LoginType.PLATFORM_ADMIN);
        assertThat(account).isNotNull();
        assertThat(account.userId()).isEqualTo(5001L);
        assertThat(account.passwordHash()).startsWith("$2a$");
        assertThat(account.enabled()).isTrue();
        assertThat(account.mustChangePassword()).isTrue();
    }

    @Test
    void typeMismatchReturnsNull() {
        ConfigAccountAuthProvider provider = new ConfigAccountAuthProvider(propertiesWithAccounts());
        assertThat(provider.findAccount("cfg-admin", LoginType.TENANT_USER)).isNull();
    }

    @Test
    void unknownUsernameReturnsNull() {
        ConfigAccountAuthProvider provider = new ConfigAccountAuthProvider(propertiesWithAccounts());
        assertThat(provider.findAccount("nobody", LoginType.TENANT_USER)).isNull();
        assertThat(provider.findAccount(null, LoginType.TENANT_USER)).isNull();
    }

    @Test
    void disabledFlagCarriedThrough() {
        ConfigAccountAuthProvider provider = new ConfigAccountAuthProvider(propertiesWithAccounts());
        PasswordAccount account = provider.findAccount("cfg-disabled", LoginType.TENANT_USER);
        assertThat(account.enabled()).isFalse();
    }

    @Test
    void findByUserIdMatches() {
        ConfigAccountAuthProvider provider = new ConfigAccountAuthProvider(propertiesWithAccounts());
        assertThat(provider.findAccountByUserId(5001L, LoginType.PLATFORM_ADMIN).username()).isEqualTo("cfg-admin");
        assertThat(provider.findAccountByUserId(9999L, LoginType.TENANT_USER)).isNull();
    }
}
