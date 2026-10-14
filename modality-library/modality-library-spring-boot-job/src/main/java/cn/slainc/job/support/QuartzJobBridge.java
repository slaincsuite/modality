package cn.slainc.job.support;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Quartz 触发桥：JobDetail 只带任务名，真正执行回落到调度器上下文里的 JobScheduler
public class QuartzJobBridge implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzJobBridge.class);

    @Override
    public void execute(JobExecutionContext context) {
        String name = context.getJobDetail().getKey().getName();
        Object target;
        try {
            Scheduler scheduler = context.getScheduler();
            target = scheduler.getContext().get(JobSchedulerImpl.SCHEDULER_CONTEXT_KEY);
        } catch (SchedulerException e) {
            LOGGER.error("触发被忽略，无法读取 Quartz 调度器上下文 name={}", name, e);
            return;
        }
        if (!(target instanceof JobSchedulerImpl jobScheduler)) {
            LOGGER.error("触发被忽略，Quartz 调度器上没有绑定 JobScheduler name={}", name);
            return;
        }
        jobScheduler.executeScheduled(name);
    }
}
