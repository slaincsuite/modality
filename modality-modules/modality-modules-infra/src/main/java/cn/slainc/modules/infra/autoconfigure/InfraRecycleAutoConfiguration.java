package cn.slainc.modules.infra.autoconfigure;

import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.entity.NoticeRecord;
import cn.slainc.modules.infra.mapper.NoticeMapper;
import cn.slainc.modules.infra.mapper.RecycleItemMapper;
import cn.slainc.modules.infra.service.RecycleBinService;
import cn.slainc.modules.infra.web.RecycleBinController;
import cn.slainc.recycle.config.RecycleProperties;
import cn.slainc.recycle.registry.RecycleBinRegistry;
import cn.slainc.recycle.registry.RecyclableType;
import cn.slainc.recycle.support.RecycleSnapshotCodec;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(afterName = "cn.slainc.recycle.autoconfigure.RecycleAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.recycle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InfraRecycleAutoConfiguration {

    @Bean(name = "noticeRecyclableType")
    @ConditionalOnMissingBean(name = "noticeRecyclableType")
    public RecyclableType noticeRecyclableType(ObjectProvider<NoticeMapper> noticeMapper) {
        return RecyclableType.of(InfraKeys.RECYCLE_TYPE_NOTICE, NoticeRecord.class, noticeMapper.getIfAvailable(),
                NoticeRecord::getId, NoticeRecord::getTitle, false);
    }

    @Bean
    @ConditionalOnMissingBean
    public RecycleBinService recycleBinService(RecycleBinRegistry registry,
                                               ObjectProvider<RecycleItemMapper> recycleItemMapper,
                                               RecycleSnapshotCodec snapshotCodec,
                                               RecycleProperties properties,
                                               ObjectProvider<JdbcTemplate> jdbcTemplate,
                                               ObjectProvider<DataSource> dataSource,
                                               ObjectProvider<CurrentUser> currentUser) {
        return new RecycleBinService(registry, recycleItemMapper.getIfAvailable(), snapshotCodec, properties,
                resolveJdbcTemplate(jdbcTemplate, dataSource), currentUser.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RecycleBinService.class)
    public RecycleBinController recycleBinController(RecycleBinService recycleBinService) {
        return new RecycleBinController(recycleBinService);
    }

    private static JdbcTemplate resolveJdbcTemplate(ObjectProvider<JdbcTemplate> jdbcTemplate,
                                                    ObjectProvider<DataSource> dataSource) {
        JdbcTemplate template = jdbcTemplate.getIfAvailable();
        if (template != null) {
            return template;
        }
        DataSource resolved = dataSource.getIfAvailable();
        return resolved == null ? null : new JdbcTemplate(resolved);
    }
}
