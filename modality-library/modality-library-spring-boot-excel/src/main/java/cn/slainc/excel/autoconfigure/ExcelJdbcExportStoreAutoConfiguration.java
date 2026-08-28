package cn.slainc.excel.autoconfigure;

import cn.slainc.excel.center.JdbcExportTaskStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(before = ExcelAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean(JdbcTemplate.class)
@AutoConfigureAfter(name = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.excel", name = "enabled", matchIfMissing = true)
public class ExcelJdbcExportStoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ExcelJdbcExportStoreAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(JdbcExportTaskStore.class)
    @ConditionalOnProperty(prefix = "modality.excel.export-center", name = {"enabled", "jdbc-store-enabled"},
            matchIfMissing = true)
    public JdbcExportTaskStore jdbcExportTaskStore(JdbcTemplate jdbcTemplate) {
        JdbcExportTaskStore store = new JdbcExportTaskStore(jdbcTemplate);
        try {
            store.initSchema();
        } catch (RuntimeException exception) {
            log.warn("导出任务表初始化失败，将在首次使用时重试：{}", exception.getMessage());
        }
        return store;
    }
}
