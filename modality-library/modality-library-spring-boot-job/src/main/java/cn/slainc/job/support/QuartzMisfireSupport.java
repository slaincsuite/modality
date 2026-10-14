package cn.slainc.job.support;

import cn.slainc.job.enums.MisfirePolicy;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;

import java.util.TimeZone;

public final class QuartzMisfireSupport {

    private QuartzMisfireSupport() {
    }

    public static int instructionOf(MisfirePolicy policy) {
        return policy == MisfirePolicy.FIRE_ONCE_NOW
                ? CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW
                : CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING;
    }

    public static CronScheduleBuilder scheduleBuilder(String quartzCron, MisfirePolicy policy) {
        CronScheduleBuilder builder = CronScheduleBuilder.cronSchedule(quartzCron).inTimeZone(TimeZone.getDefault());
        return switch (policy == null ? MisfirePolicy.SKIP : policy) {
            case SKIP -> builder.withMisfireHandlingInstructionDoNothing();
            case FIRE_ONCE_NOW -> builder.withMisfireHandlingInstructionFireAndProceed();
        };
    }
}
