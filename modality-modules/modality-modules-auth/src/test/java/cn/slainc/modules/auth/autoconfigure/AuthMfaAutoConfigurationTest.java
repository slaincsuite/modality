package cn.slainc.modules.auth.autoconfigure;

import cn.slainc.mfa.spi.MfaChallengeStore;
import cn.slainc.mfa.authentication.SecondFactorGate;
import cn.slainc.mfa.spi.MfaFactorPort;
import cn.slainc.mfa.spi.MfaUserLoader;
import cn.slainc.modules.auth.mfa.AuthMfaUserLoader;
import cn.slainc.modules.auth.mfa.MfaLoginTicket;
import cn.slainc.modules.auth.service.AuthLoginService;
import cn.slainc.modules.auth.service.AuthMfaService;
import cn.slainc.modules.auth.service.AuthSecondFactorGate;
import cn.slainc.modules.auth.service.AuthSessionService;
import cn.slainc.modules.auth.store.AuthTicketStore;
import cn.slainc.modules.auth.store.JdbcMfaFactorStore;
import cn.slainc.modules.auth.web.AuthMfaController;
import cn.slainc.security.spi.AccountAuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthMfaAutoConfigurationTest {

    private final javax.sql.DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:mfaAutoConfig" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(cn.slainc.mfa.autoconfigure.MfaAutoConfiguration.class,
                    AuthMfaAutoConfiguration.class))
            .withBean("dataSource", javax.sql.DataSource.class, () -> dataSource)
            .withBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource))
            .withBean(AccountAuthProvider.class, () -> mock(AccountAuthProvider.class))
            .withBean(AuthSessionService.class, () -> mock(AuthSessionService.class))
            .withBean(AuthLoginService.class, () -> mock(AuthLoginService.class));

    @Test
    void shouldAssembleTheMfaIntegrationByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AuthMfaService.class);
            assertThat(context).hasSingleBean(AuthSecondFactorGate.class);
            assertThat(context).hasSingleBean(JdbcMfaFactorStore.class);
            assertThat(context).hasSingleBean(AuthMfaUserLoader.class);
            assertThat(context).hasSingleBean(AuthMfaController.class);
            assertThat(context).hasSingleBean(MfaChallengeStore.class);
            assertThat(context).hasBean("mfaLoginTicketStore");
            assertThat(context.getBean("mfaLoginTicketStore"))
                    .isInstanceOf(AuthTicketStore.class);
            assertThat(context.getBean(MfaFactorPort.class)).isInstanceOf(JdbcMfaFactorStore.class);
            assertThat(context.getBean(MfaUserLoader.class)).isInstanceOf(AuthMfaUserLoader.class);
            assertThat(context.getBean(SecondFactorGate.class).method()).isEqualTo("TOTP");

            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            Integer tables = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'T_MFA_FACTOR'", Integer.class);
            assertThat(tables).isEqualTo(1);
        });
    }

    @Test
    void shouldBackOffEntirelyWhenMfaIsDisabled() {
        runner.withPropertyValues("modality.mfa.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AuthMfaService.class);
            assertThat(context).doesNotHaveBean(AuthSecondFactorGate.class);
            assertThat(context).doesNotHaveBean(JdbcMfaFactorStore.class);
            assertThat(context).doesNotHaveBean(AuthMfaUserLoader.class);
            assertThat(context).doesNotHaveBean(MfaChallengeStore.class);
            assertThat(context).doesNotHaveBean(AuthMfaController.class);
        });
    }

    @Test
    void shouldLetTheTicketStoreLiveInMemoryWithoutRedis() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            AuthTicketStore<MfaLoginTicket> store = context.getBean("mfaLoginTicketStore", AuthTicketStore.class);
            MfaLoginTicket ticket = MfaLoginTicket.of("challenge-1", 1L, "alice", "TENANT_USER", "PC", null,
                    false, 60);
            store.save(ticket, 60);
            assertThat(store.find("challenge-1")).isPresent();
            assertThat(store.consume("challenge-1")).isPresent();
            assertThat(store.find("challenge-1")).isEmpty();
        });
    }
}
