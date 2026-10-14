package cn.slainc.job.support;

import java.time.Duration;

// 分布式锁约定：基于 modality Redis 库的 Redisson 锁等价替换 ShedLock，
// 锁名 modality:job:<任务名>，leaseTime 即 lockAtMostFor（超时时间或默认上限），等待 0 秒即抢不到立即跳过
public final class JobLockSupport {

    public static final String LOCK_NAME_PREFIX = "modality:job:";

    public static final Duration DEFAULT_LOCK_AT_MOST = Duration.ofMinutes(30);

    private static final long MIN_LEASE_SECONDS = 1L;

    private JobLockSupport() {
    }

    public static String lockName(String jobName) {
        return LOCK_NAME_PREFIX + jobName;
    }

    public static String startupCompensationLockName(String jobName) {
        return lockName(jobName) + ":startup-compensation";
    }

    public static long lockAtMostForSeconds(JobDefinition definition, Duration configuredDefault) {
        Duration candidate = definition != null && definition.hasTimeout() ? definition.timeout() : configuredDefault;
        if (candidate == null || candidate.isZero() || candidate.isNegative()) {
            candidate = DEFAULT_LOCK_AT_MOST;
        }
        return Math.max(candidate.toSeconds(), MIN_LEASE_SECONDS);
    }
}
