package cn.slainc.excel.autoconfigure;

import cn.slainc.excel.center.ExportCenter;
import cn.slainc.excel.center.ExportTaskStore;
import cn.slainc.excel.center.InMemoryExportTaskStore;
import cn.slainc.excel.center.JdbcExportTaskStore;
import cn.slainc.excel.imports.ImportLog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ExcelAutoConfiguration.class,
                    ExcelJdbcExportStoreAutoConfiguration.class));

    @Test
    void registersDefaultBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ExcelProperties.class);
            assertThat(context).hasSingleBean(ExportTaskStore.class);
            assertThat(context).hasSingleBean(InMemoryExportTaskStore.class);
            assertThat(context).hasSingleBean(ExportCenter.class);
            assertThat(context).hasSingleBean(ImportLog.class);
            assertThat(context.getBean(ExcelProperties.class).getExportCenter().isEnabled()).isTrue();
        });
    }

    @Test
    void backsOffWhenCapabilityDisabled() {
        runner.withPropertyValues("modality.excel.export-center.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(ExportCenter.class);
            assertThat(context).doesNotHaveBean(ExportTaskStore.class);
            assertThat(context).hasSingleBean(ImportLog.class);
        });
        runner.withPropertyValues("modality.excel.import-log.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(ImportLog.class);
            assertThat(context).hasSingleBean(ExportCenter.class);
        });
    }

    @Test
    void backsOffWhenModuleDisabled() {
        runner.withPropertyValues("modality.excel.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(ExportCenter.class);
            assertThat(context).doesNotHaveBean(ImportLog.class);
        });
    }

    @Test
    void switchesToJdbcStoreWhenJdbcTemplatePresent() {
        runner.withBean(JdbcTemplate.class, () -> new JdbcTemplate(new DriverManagerDataSource(
                        "jdbc:h2:mem:excel_auto;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")))
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcExportTaskStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryExportTaskStore.class);
                    assertThat(context).hasSingleBean(ExportCenter.class);
                    assertThat(context.getBean(ExportTaskStore.class)).isInstanceOf(JdbcExportTaskStore.class);
                });
    }
}
