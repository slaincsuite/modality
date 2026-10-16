package cn.slainc.changeaudit.autoconfigure;

import cn.slainc.changeaudit.spi.FieldDiffEngine;
import cn.slainc.changeaudit.support.FieldDiffer;
import cn.slainc.changeaudit.support.JaVersFieldDiffer;
import cn.slainc.changeaudit.aspect.ChangeAuditAspect;
import cn.slainc.changeaudit.event.AfterCommitExecutor;
import cn.slainc.changeaudit.spi.ChangeRecorder;
import cn.slainc.changeaudit.spi.SnapshotLoader;
import cn.slainc.changeaudit.support.AsyncChangeRecorder;
import cn.slainc.changeaudit.support.NoopChangeRecorder;
import cn.slainc.changeaudit.support.SnapshotLoaderRegistry;
import cn.slainc.changeaudit.dao.JdbcChangeRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeAuditAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChangeAuditAutoConfiguration.class));

    private final ApplicationContextRunner jdbcRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChangeAuditAutoConfiguration.class,
                    ChangeAuditJdbcAutoConfiguration.class))
            .withBean("changeAuditTestDataSource", SimpleDriverDataSource.class, ChangeAuditAutoConfigurationTest::dataSource)
            .withBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource()));

    private static SimpleDriverDataSource dataSource() {
        return new SimpleDriverDataSource(new org.h2.Driver(),
                "jdbc:h2:mem:change-audit-autoconfig;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Test
    void registersTheChangeAuditBeans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ChangeAuditConfigReader.class);
            assertThat(context).hasSingleBean(SnapshotLoaderRegistry.class);
            assertThat(context).hasSingleBean(FieldDiffer.class);
            assertThat(context).hasSingleBean(AfterCommitExecutor.class);
            assertThat(context).hasSingleBean(ChangeAuditAspect.class);
            assertThat(context).doesNotHaveBean(ChangeRecorder.class);
        });
    }

    @Test
    void registersNothingWhenDisabled() {
        runner.withPropertyValues("modality.change-audit.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ChangeAuditAspect.class);
            assertThat(context).doesNotHaveBean(ChangeAuditConfigReader.class);
        });
    }

    @Test
    void fallsBackToTheNoopRecorderWithoutAnSpiImplementation() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(effectiveRecorder(context)).isInstanceOf(NoopChangeRecorder.class);
        });
    }

    @Test
    void usesTheSpiImplementationWhenRegistered() {
        ChangeRecorder recorder = change -> {
        };
        runner.withBean(ChangeRecorder.class, () -> recorder).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(effectiveRecorder(context)).isSameAs(recorder);
        });
    }

    @Test
    void wrapsTheRecorderWhenAsyncIsEnabled() {
        ChangeRecorder recorder = change -> {
        };
        runner.withPropertyValues("modality.change-audit.async=true")
                .withBean(ChangeRecorder.class, () -> recorder)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("changeAuditExecutor");
                    Object effective = effectiveRecorder(context);
                    assertThat(effective).isInstanceOf(AsyncChangeRecorder.class);
                    assertThat(ReflectionTestUtils.getField(effective, "delegate")).isSameAs(recorder);
                });
    }

    @Test
    void keepsTheBuiltinEngineByDefaultAndSwitchesToJaVers() {
        runner.run(context -> assertThat(context.getBean(FieldDiffEngine.class)).isInstanceOf(FieldDiffer.class));

        runner.withPropertyValues("modality.change-audit.diff-engine=javers")
                .run(context -> assertThat(context.getBean(FieldDiffEngine.class))
                        .isInstanceOf(JaVersFieldDiffer.class));
    }

    @Test
    void collectsTheSnapshotLoaders() {
        SnapshotLoader loader = new SnapshotLoader() {
            @Override
            public String type() {
                return "TENANT";
            }

            @Override
            public Object load(String id) {
                return null;
            }
        };
        runner.withBean("tenantSnapshotLoader", SnapshotLoader.class, () -> loader).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SnapshotLoaderRegistry.class).types()).containsExactly("TENANT");
        });
    }

    private static Object effectiveRecorder(ApplicationContext context) {
        return ReflectionTestUtils.getField(context.getBean(ChangeAuditAspect.class), "changeRecorder");
    }

    @Test
    void fallsBackToTheJdbcRecorderAndCreatesTheTableByDefault() {
        jdbcRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JdbcChangeRecorder.class);
            assertThat(context).hasBean("changeAuditSchemaInitializer");
            assertThat(effectiveRecorder(context)).isInstanceOf(JdbcChangeRecorder.class);
            Integer rows = context.getBean(JdbcTemplate.class)
                    .queryForObject("SELECT COUNT(*) FROM t_change_audit_record", Integer.class);
            assertThat(rows).isZero();
        });
    }

    @Test
    void storeNoopKeepsTheNoopRecorder() {
        jdbcRunner.withPropertyValues("modality.change-audit.store=noop").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(JdbcChangeRecorder.class);
            assertThat(effectiveRecorder(context)).isInstanceOf(NoopChangeRecorder.class);
        });
    }

    @Test
    void customRecorderWinsOverTheJdbcRecorder() {
        ChangeRecorder recorder = change -> {
        };
        jdbcRunner.withBean(ChangeRecorder.class, () -> recorder).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(JdbcChangeRecorder.class);
            assertThat(effectiveRecorder(context)).isSameAs(recorder);
        });
    }

    @Test
    void autoCreateTableCanBeDisabled() {
        jdbcRunner.withPropertyValues("modality.change-audit.jdbc.auto-create-table=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("changeAuditSchemaInitializer");
            assertThat(context).hasSingleBean(JdbcChangeRecorder.class);
        });
    }
}
