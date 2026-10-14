package cn.slainc.webhook.autoconfigure;

import cn.slainc.redis.lock.DistributedLockTemplate;
import cn.slainc.webhook.api.WebhookSender;
import cn.slainc.webhook.api.impl.HttpWebhookSender;
import cn.slainc.webhook.config.WebhookProperties;
import cn.slainc.webhook.spi.WebhookDeliveryPort;
import cn.slainc.webhook.support.WebhookDeliveryDispatcher;
import cn.slainc.webhook.support.WebhookRetryScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@AutoConfiguration(afterName = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.webhook", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(WebhookProperties.class)
public class WebhookAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(WebhookSender.class)
    public WebhookSender webhookSender(WebhookProperties properties) {
        return new HttpWebhookSender(properties);
    }

    @Bean(name = "webhookDeliveryExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "webhookDeliveryExecutor")
    public ThreadPoolTaskExecutor webhookDeliveryExecutor(WebhookProperties properties) {
        int poolSize = Math.max(properties.getThreadPoolSize(), 1);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("modality-webhook-delivery-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean(WebhookDeliveryDispatcher.class)
    public WebhookDeliveryDispatcher webhookDeliveryDispatcher(WebhookSender sender, WebhookProperties properties,
                                                               ObjectProvider<WebhookDeliveryPort> portProvider) {
        return new WebhookDeliveryDispatcher(sender, properties, portProvider);
    }

    @Bean(name = "webhookRetryScheduler", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "webhookRetryScheduler")
    public ThreadPoolTaskScheduler webhookRetryScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("modality-webhook-retry-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean(WebhookRetryScheduler.class)
    public WebhookRetryScheduler webhookRetrySchedulerRunner(WebhookDeliveryDispatcher dispatcher,
                                                             WebhookProperties properties,
                                                             ObjectProvider<DistributedLockTemplate> lockProvider,
                                                             @Qualifier("webhookRetryScheduler")
                                                             ThreadPoolTaskScheduler scheduler) {
        WebhookRetryScheduler runner = new WebhookRetryScheduler(dispatcher, properties, lockProvider, scheduler);
        runner.start();
        return runner;
    }
}
