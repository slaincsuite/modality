package cn.slainc.excel.autoconfigure;

import cn.slainc.excel.center.ExportCenter;
import cn.slainc.excel.center.ExportTaskStore;
import cn.slainc.excel.center.InMemoryExportTaskStore;
import cn.slainc.excel.imports.ImportLog;
import cn.slainc.excel.imports.ImportLogStore;
import cn.slainc.excel.imports.InMemoryImportLogStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

@AutoConfiguration
@EnableConfigurationProperties(ExcelProperties.class)
@ConditionalOnProperty(prefix = "modality.excel", name = "enabled", matchIfMissing = true)
public class ExcelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ExportTaskStore.class)
    @ConditionalOnProperty(prefix = "modality.excel.export-center", name = "enabled", matchIfMissing = true)
    public ExportTaskStore exportTaskStore() {
        return new InMemoryExportTaskStore();
    }

    @Bean
    @ConditionalOnMissingBean(ExportCenter.class)
    @ConditionalOnProperty(prefix = "modality.excel.export-center", name = "enabled", matchIfMissing = true)
    public ExportCenter exportCenter(ExportTaskStore exportTaskStore, ExcelProperties properties) {
        ExcelProperties.ExportCenterSettings settings = properties.getExportCenter();
        String storagePath = settings.getStoragePath();
        Path directory = storagePath == null || storagePath.isBlank() ? null : Path.of(storagePath);
        return new ExportCenter(exportTaskStore, directory, settings.getBatchSize());
    }

    @Bean
    @ConditionalOnMissingBean(ImportLogStore.class)
    @ConditionalOnProperty(prefix = "modality.excel.import-log", name = "enabled", matchIfMissing = true)
    public ImportLogStore importLogStore(ExcelProperties properties) {
        return new InMemoryImportLogStore(properties.getImportLog().getMaxEntries());
    }

    @Bean
    @ConditionalOnMissingBean(ImportLog.class)
    @ConditionalOnProperty(prefix = "modality.excel.import-log", name = "enabled", matchIfMissing = true)
    public ImportLog importLog(ImportLogStore importLogStore) {
        return new ImportLog(importLogStore);
    }
}
