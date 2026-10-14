package cn.slainc.recycle.autoconfigure;

import cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration;
import cn.slainc.recycle.config.RecycleProperties;
import cn.slainc.recycle.cleaner.RecycleBinCleaner;
import cn.slainc.recycle.registry.RecycleBinRegistry;
import cn.slainc.recycle.registry.RecyclableType;
import cn.slainc.recycle.spi.RecycleItemStore;
import cn.slainc.recycle.support.RecycleSnapshotCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@AutoConfiguration(after = MybatisAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.recycle", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(RecycleProperties.class)
public class RecycleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RecycleBinRegistry.class)
    public RecycleBinRegistry recycleBinRegistry(ObjectProvider<RecyclableType> typeProvider,
                                                 RecycleProperties properties) {
        return new RecycleBinRegistry(typeProvider.orderedStream().toList(), properties);
    }

    @Bean
    @ConditionalOnMissingBean(RecycleSnapshotCodec.class)
    public RecycleSnapshotCodec recycleSnapshotCodec(ObjectProvider<ObjectMapper> mapperProvider,
                                                     RecycleProperties properties) {
        ObjectMapper objectMapper = mapperProvider.getIfUnique();
        return new RecycleSnapshotCodec(objectMapper, properties.getMaxSnapshotBytes());
    }

    @Bean
    @ConditionalOnMissingBean(RecycleBinCleaner.class)
    public RecycleBinCleaner recycleBinCleaner(ObjectProvider<RecycleItemStore> recycleItemStoreProvider) {
        return new RecycleBinCleaner(recycleItemStoreProvider);
    }

    @Bean(name = "recycleBinScheduler", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "recycleBinScheduler")
    public ThreadPoolTaskScheduler recycleBinScheduler(RecycleBinCleaner cleaner, RecycleProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("modality-recycle-cleaner-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        Duration interval = properties.getSchedulerInterval();
        if (interval != null && !interval.isZero() && !interval.isNegative()) {
            scheduler.scheduleWithFixedDelay(cleaner::purgeExpired, interval);
        }
        return scheduler;
    }
}
