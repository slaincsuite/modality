package cn.slainc.job.support;

import cn.slainc.job.enums.MisfirePolicy;

import java.io.Serializable;
import java.time.Duration;

public record JobDefinition(String name, String cron, String description, int retryCount, Duration retryInterval,
                            Duration timeout, boolean concurrent, MisfirePolicy misfirePolicy, JobHandler handler,
                            Long tenantId)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(5);

    public JobDefinition {
        retryCount = Math.max(retryCount, 0);
        retryInterval = retryInterval == null ? DEFAULT_RETRY_INTERVAL : retryInterval;
        misfirePolicy = misfirePolicy == null ? MisfirePolicy.SKIP : misfirePolicy;
    }

    public JobDefinition(String name, String cron, String description, int retryCount, Duration retryInterval,
                         JobHandler handler) {
        this(name, cron, description, retryCount, retryInterval, null, true, MisfirePolicy.SKIP, handler, null);
    }

    public JobDefinition(String name, String cron, String description, int retryCount, Duration retryInterval,
                         Duration timeout, boolean concurrent, MisfirePolicy misfirePolicy, JobHandler handler) {
        this(name, cron, description, retryCount, retryInterval, timeout, concurrent, misfirePolicy, handler, null);
    }

    public boolean hasCron() {
        return cron != null && !cron.isBlank();
    }

    public boolean hasTimeout() {
        return timeout != null && !timeout.isZero() && !timeout.isNegative();
    }
}
