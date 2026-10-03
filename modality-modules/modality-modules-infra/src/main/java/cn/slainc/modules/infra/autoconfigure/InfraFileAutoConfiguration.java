package cn.slainc.modules.infra.autoconfigure;

import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.service.ChunkUploadService;
import cn.slainc.modules.infra.service.FileManagerService;
import cn.slainc.modules.infra.store.FileRecordStore;
import cn.slainc.modules.infra.service.FileUploadService;
import cn.slainc.modules.infra.web.FileController;
import cn.slainc.storage.StorageTemplate;
import cn.slainc.storage.preview.FilePreviewService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(afterName = {"cn.slainc.storage.autoconfigure.StorageAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration"})
@ConditionalOnBean({StorageTemplate.class, JdbcTemplate.class})
@ConditionalOnProperty(prefix = "modality.infra", name = "enabled", matchIfMissing = true)
@Import(FileController.class)
public class InfraFileAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FileRecordStore fileRecordStore(JdbcTemplate jdbc) {
        return new FileRecordStore(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    public FileUploadService fileUploadService(StorageTemplate storageTemplate, FileRecordStore recordStore,
                                               CurrentUser currentUser) {
        return new FileUploadService(storageTemplate, recordStore, currentUser);
    }

    @Bean
    @ConditionalOnMissingBean
    public FileManagerService fileManagerService(StorageTemplate storageTemplate, FileRecordStore recordStore,
                                                 FilePreviewService previewService,
                                                 InfraModuleProperties properties) {
        return new FileManagerService(storageTemplate, recordStore, previewService,
                properties.getFile().getPreviewTextMaxBytes());
    }

    @Bean
    @ConditionalOnMissingBean
    public ChunkUploadService chunkUploadService(StorageTemplate storageTemplate, FileRecordStore recordStore,
                                                 CurrentUser currentUser, InfraModuleProperties properties) {
        return new ChunkUploadService(storageTemplate, recordStore, currentUser,
                properties.getFile().getChunkContextMax());
    }
}
