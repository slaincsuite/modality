package cn.slainc.excel.autoconfigure;

import cn.slainc.excel.center.JdbcExportTaskStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(before = ExcelAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean(JdbcTemplate.class)
@AutoConfigureAfter(name = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.excel", name = "enabled", matchIfMissing = true)
public class ExcelJdbcExportStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JdbcExportTaskStore.class)
    @ConditionalOnProperty(prefix = "modality.excel.export-center", name = "enabled", matchIfMissing = true)
    public JdbcExportTaskStore jdbcExportTaskStore(JdbcTemplate jdbcTemplate) {
        return new JdbcExportTaskStore(jdbcTemplate);
    }
}
