package cn.slainc.job.support;

import java.io.Serializable;

public record JobTriggerAck(String traceId, String jobName, boolean accepted, String message)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public static JobTriggerAck accepted(String traceId, String jobName) {
        return new JobTriggerAck(traceId, jobName, true, "已受理");
    }

    public static JobTriggerAck rejected(String traceId, String jobName, String message) {
        return new JobTriggerAck(traceId, jobName, false, message);
    }
}
