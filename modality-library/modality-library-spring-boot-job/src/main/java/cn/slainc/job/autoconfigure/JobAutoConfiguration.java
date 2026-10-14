package cn.slainc.job.autoconfigure;

import cn.slainc.job.annotation.Job;
import cn.slainc.job.api.JobScheduler;
import cn.slainc.job.support.JobSchedulerImpl;
import cn.slainc.job.support.JobDefinition;
import cn.slainc.job.support.JobHandler;
import cn.slainc.job.support.QuartzBootstrap;
import cn.slainc.job.support.QuartzStoreCustomizer;
import cn.slainc.redis.lock.DistributedLockTemplate;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration;
import org.springframework.boot.quartz.autoconfigure.QuartzProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration(after = QuartzAutoConfiguration.class)
@EnableConfigurationProperties(JobProperties.class)
@ConditionalOnProperty(prefix = "modality.job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(QuartzStoreCustomizer.class)
    public QuartzStoreCustomizer quartzStoreCustomizer(JobProperties properties, QuartzProperties quartzProperties,
                                                       ObjectProvider<DataSource> dataSources) {
        return new QuartzStoreCustomizer(properties, quartzProperties, uniqueDataSource(dataSources));
    }

    @Bean
    @ConditionalOnMissingBean(JobScheduler.class)
    public JobSchedulerImpl jobScheduler(Scheduler quartzScheduler,
                                         ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                         ObjectProvider<DistributedLockTemplate> lockTemplateProvider,
                                         JobProperties properties,
                                         ObjectProvider<JobHandler> handlers,
                                         ObjectProvider<cn.slainc.job.support.JobRecorder> recorders) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            LOGGER.warn("没有 StringRedisTemplate Bean：任务最近结果缓存不可用");
        }
        DistributedLockTemplate lockTemplate = lockTemplateProvider.getIfAvailable();
        if (lockTemplate == null) {
            LOGGER.warn("没有 DistributedLockTemplate Bean：任务集群互斥退化为单进程锁");
        }
        LOGGER.info("任务调度内核为 Quartz，jobStore={}", jobStoreName(quartzScheduler));
        JobSchedulerImpl scheduler = new JobSchedulerImpl(redisTemplate, lockTemplate, properties, quartzScheduler);
        scheduler.setRecorderProvider(recorders);
        registerAnnotated(scheduler, handlers, properties);
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean(QuartzBootstrap.class)
    public QuartzBootstrap quartzBootstrap(Scheduler quartzScheduler, QuartzStoreCustomizer storeCustomizer) {
        return new QuartzBootstrap(quartzScheduler, storeCustomizer);
    }

    private void registerAnnotated(JobScheduler scheduler, ObjectProvider<JobHandler> handlers, JobProperties properties) {
        List<JobHandler> candidates = handlers.stream().toList();
        if (candidates.isEmpty()) {
            return;
        }
        for (JobHandler handler : candidates) {
            Job annotation = AnnotatedElementUtils.findMergedAnnotation(AopUtils.getTargetClass(handler), Job.class);
            if (annotation == null || !annotation.enabled()) {
                continue;
            }
            String name = annotation.name() != null && !annotation.name().isBlank()
                    ? annotation.name()
                    : handler.getClass().getSimpleName();
            scheduler.register(new JobDefinition(name, annotation.cron(), annotation.description(),
                    annotation.retryCount(), properties.getDefaultRetryInterval(), handler));
        }
    }

    private String jobStoreName(Scheduler scheduler) {
        try {
            return scheduler.getMetaData().getJobStoreClass().getSimpleName();
        } catch (SchedulerException e) {
            // 读取失败仅影响日志展示
            return "unknown";
        }
    }

    private DataSource uniqueDataSource(ObjectProvider<DataSource> dataSources) {
        try {
            return dataSources.getIfUnique();
        } catch (NoUniqueBeanDefinitionException e) {
            LOGGER.warn("容器中存在多个 DataSource 且无主 Bean：Quartz 退回 RAMJobStore；"
                    + "设置 modality.job.store=jdbc 并保证唯一 DataSource 可启用 JDBC 存储");
            return null;
        }
    }
}
