package cn.slainc.modules.job.support;

import cn.slainc.job.support.QuartzCronSupport;

import java.time.LocalDateTime;
import java.util.List;

public final class CronSupport {

    private CronSupport() {
    }

    public static boolean isValid(String cron) {
        if (cron == null || cron.isBlank()) {
            return false;
        }
        return QuartzCronSupport.isConvertible(cron.trim());
    }

    public static List<LocalDateTime> nextExecutions(String cron, int count) {
        if (!isValid(cron)) {
            return List.of();
        }
        return QuartzCronSupport.nextExecutions(cron.trim(), count);
    }
}
