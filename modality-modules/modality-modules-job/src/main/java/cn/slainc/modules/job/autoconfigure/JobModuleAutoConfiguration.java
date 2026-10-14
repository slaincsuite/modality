package cn.slainc.modules.job.autoconfigure;

import cn.slainc.job.api.JobScheduler;
import cn.slainc.job.support.JobHandler;
import cn.slainc.modules.job.mapper.JobLogMapper;
import cn.slainc.modules.job.mapper.JobTaskMapper;
import cn.slainc.modules.job.service.JobManageService;
import cn.slainc.modules.job.support.BuiltinJobHandlers;
import cn.slainc.modules.job.scheduler.JobMaintenanceScheduler;
import cn.slainc.modules.job.store.NodeRegistry;
import cn.slainc.modules.job.web.JobLogController;
import cn.slainc.modules.job.web.JobNodeController;
import cn.slainc.modules.job.web.JobTaskController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Map;

@AutoConfiguration
@ConditionalOnProperty(prefix = "modality.job", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JobModuleProperties.class)
public class JobModuleAutoConfiguration {

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(NodeRegistry.class)
    public NodeRegistry jobNodeRegistry(ObjectProvider<StringRedisTemplate> redisTemplate,
                                        JobModuleProperties properties) {
        return new NodeRegistry(redisTemplate.getIfAvailable(), properties.getKeyPrefix(), properties.getNodeTtl(),
                properties.getNodeId());
    }

    // 控制器跟随服务条件注册：没有 JobScheduler 时服务与端点一起退避
    @Bean
    @ConditionalOnMissingBean(JobTaskController.class)
    @ConditionalOnBean(JobManageService.class)
    public JobTaskController jobTaskController(JobManageService jobManageService) {
        return new JobTaskController(jobManageService);
    }

    @Bean
    @ConditionalOnMissingBean(JobLogController.class)
    @ConditionalOnBean(JobManageService.class)
    public JobLogController jobLogController(JobManageService jobManageService) {
        return new JobLogController(jobManageService);
    }

    @Bean
    @ConditionalOnMissingBean(JobNodeController.class)
    public JobNodeController jobNodeController(NodeRegistry nodeRegistry) {
        return new JobNodeController(nodeRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(JobManageService.class)
    @ConditionalOnBean(JobScheduler.class)
    public JobManageService jobManageService(JobTaskMapper jobTaskMapper, JobLogMapper jobLogMapper,
                                             JobScheduler jobScheduler, NodeRegistry nodeRegistry,
                                             JobModuleProperties properties,
                                             ObjectProvider<Map<String, JobHandler>> handlerBeans) {
        Map<String, JobHandler> handlers = handlerBeans.getIfAvailable();
        return new JobManageService(jobTaskMapper, jobLogMapper, jobScheduler, nodeRegistry, properties,
                handlers == null ? Map.of() : handlers);
    }

    @Bean(name = BuiltinJobHandlers.JOB_LOG_PURGE)
    @ConditionalOnMissingBean(name = BuiltinJobHandlers.JOB_LOG_PURGE)
    @ConditionalOnBean(JobScheduler.class)
    public JobHandler jobLogPurgeJob(JobLogMapper jobLogMapper, JobModuleProperties properties) {
        return BuiltinJobHandlers.jobLogPurgeJob(jobLogMapper, properties);
    }

    @Bean(name = BuiltinJobHandlers.NODE_HEARTBEAT)
    @ConditionalOnMissingBean(name = BuiltinJobHandlers.NODE_HEARTBEAT)
    @ConditionalOnBean(JobScheduler.class)
    public JobHandler nodeHeartbeatJob(NodeRegistry nodeRegistry) {
        return BuiltinJobHandlers.nodeHeartbeatJob(nodeRegistry);
    }

    @Bean(name = "jobNodeHeartbeatScheduler", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "jobNodeHeartbeatScheduler")
    public ThreadPoolTaskScheduler jobNodeHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setThreadFactory(Thread.ofPlatform().daemon().name("modality-job-node-", 0L).factory());
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean(JobMaintenanceScheduler.class)
    @ConditionalOnBean(JobManageService.class)
    public JobMaintenanceScheduler jobMaintenanceScheduler(
            @Qualifier("jobNodeHeartbeatScheduler") ThreadPoolTaskScheduler jobNodeHeartbeatScheduler,
            NodeRegistry nodeRegistry, JobManageService jobManageService, JobModuleProperties properties) {
        return new JobMaintenanceScheduler(jobNodeHeartbeatScheduler, nodeRegistry, jobManageService, properties);
    }
}
