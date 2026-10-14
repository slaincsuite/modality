package cn.slainc.modules.job.support;

import cn.slainc.job.support.JobHandler;
import cn.slainc.modules.job.store.NodeRegistry;
import cn.slainc.job.support.JobResult;
import cn.slainc.modules.job.autoconfigure.JobModuleProperties;
import cn.slainc.modules.job.entity.JobLogRecord;
import cn.slainc.modules.job.mapper.JobLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.Duration;

// 内置任务处理器：日志清理与节点心跳，按 Bean 名注册进处理器索引
public final class BuiltinJobHandlers {

    public static final String JOB_LOG_PURGE = "jobLogPurgeJob";

    public static final String NODE_HEARTBEAT = "nodeHeartbeatJob";

    private BuiltinJobHandlers() {
    }

    public static JobHandler jobLogPurgeJob(JobLogMapper jobLogMapper, JobModuleProperties properties) {
        return context -> {
            long start = System.currentTimeMillis();
            Duration retention = properties == null ? null : properties.getLogRetention();
            if (retention == null || retention.isZero() || retention.isNegative()) {
                return new JobResult(true, "日志保留策略未开启，无需清理", System.currentTimeMillis() - start);
            }
            long threshold = System.currentTimeMillis() - retention.toMillis();
            int deleted = jobLogMapper.delete(new LambdaQueryWrapper<JobLogRecord>()
                    .lt(JobLogRecord::getFireTime, threshold));
            return new JobResult(true, "清理了 " + deleted + " 条过期任务日志",
                    System.currentTimeMillis() - start);
        };
    }

    public static JobHandler nodeHeartbeatJob(NodeRegistry nodeRegistry) {
        return context -> {
            long start = System.currentTimeMillis();
            nodeRegistry.heartbeat();
            return new JobResult(true, "节点 " + nodeRegistry.nodeId() + " 心跳正常",
                    System.currentTimeMillis() - start);
        };
    }
}
