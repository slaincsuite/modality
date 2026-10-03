package cn.slainc.modules.infra.autoconfigure;

import cn.slainc.logger.archive.LogArchiveService;
import cn.slainc.logger.level.LogLevelManager;
import cn.slainc.logger.query.LogFileQueryService;
import cn.slainc.modules.infra.service.LogConsoleService;
import cn.slainc.modules.infra.web.LogConsoleController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration(afterName = "cn.slainc.logger.autoconfigure.LoggerAutoConfiguration")
@ConditionalOnBean(LogFileQueryService.class)
@ConditionalOnProperty(prefix = "modality.infra", name = "enabled", matchIfMissing = true)
@Import(LogConsoleController.class)
public class InfraLogConsoleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LogConsoleService logConsoleService(LogFileQueryService queryService,
                                               ObjectProvider<LogLevelManager> levelManager,
                                               ObjectProvider<LogArchiveService> archiveService) {
        return new LogConsoleService(queryService, levelManager, archiveService);
    }
}
