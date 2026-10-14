package cn.slainc.job.support;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

public record JobContext(String jobName, Map<String, Object> params, LocalDateTime fireTime, int attempt,
                         Long tenantId) implements Serializable {

    private static final long serialVersionUID = 1L;

    public JobContext {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public JobContext(String jobName, Map<String, Object> params, LocalDateTime fireTime, int attempt) {
        this(jobName, params, fireTime, attempt, null);
    }

    public String param(String key) {
        Object value = params.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
