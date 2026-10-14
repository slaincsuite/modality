package cn.slainc.job.support;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

// 启动引导：任务定义同步完成后才真正启动 Quartz 调度线程，避免启动窗口漏触发
public class QuartzBootstrap implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzBootstrap.class);

    private final Scheduler scheduler;

    private final QuartzStoreCustomizer customizer;

    public QuartzBootstrap(Scheduler scheduler, QuartzStoreCustomizer customizer) {
        this.scheduler = scheduler;
        this.customizer = customizer;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        start();
    }

    public void start() {
        if (!customizer.isStartupDeferred()) {
            return;
        }
        try {
            if (scheduler.isStarted()) {
                return;
            }
            scheduler.start();
            LOGGER.info("Quartz 调度器已启动，jobStore={}, 集群={}",
                    customizer.isJdbcStore() ? "JDBC" : "RAM", scheduler.getMetaData().isJobStoreClustered());
        } catch (SchedulerException e) {
            throw new IllegalStateException("启动 Quartz 调度器失败", e);
        }
    }
}
