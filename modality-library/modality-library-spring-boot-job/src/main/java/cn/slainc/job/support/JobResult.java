package cn.slainc.job.support;

import java.io.Serializable;

public record JobResult(boolean success, String message, long costMillis, JobResult.Status status, int attempts)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        SUCCESS,
        FAILURE,
        SKIPPED
    }

    public JobResult {
        status = status == null ? (success ? Status.SUCCESS : Status.FAILURE) : status;
        attempts = Math.max(attempts, 0);
    }

    public JobResult(boolean success, String message, long costMillis) {
        this(success, message, costMillis, success ? Status.SUCCESS : Status.FAILURE, 1);
    }

    public static JobResult success(long costMillis) {
        return new JobResult(true, "执行成功", costMillis, Status.SUCCESS, 1);
    }

    public static JobResult failure(String message, long costMillis) {
        return new JobResult(false, message, costMillis, Status.FAILURE, 1);
    }

    public static JobResult skipped(String message, long costMillis) {
        return new JobResult(false, message, costMillis, Status.SKIPPED, 0);
    }

    public boolean skipped() {
        return status == Status.SKIPPED;
    }

    public JobResult withAttempts(int attempts) {
        return new JobResult(success, message, costMillis, status, attempts);
    }
}
