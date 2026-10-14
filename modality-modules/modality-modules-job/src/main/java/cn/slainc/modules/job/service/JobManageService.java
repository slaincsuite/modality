package cn.slainc.modules.job.service;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.job.annotation.Job;
import cn.slainc.job.api.JobScheduler;
import cn.slainc.job.enums.MisfirePolicy;
import cn.slainc.job.support.JobContext;
import cn.slainc.job.support.JobDefinition;
import cn.slainc.job.support.JobHandler;
import cn.slainc.job.support.JobRecorder;
import cn.slainc.job.support.JobResult;
import cn.slainc.job.support.JobTriggerAck;
import cn.slainc.modules.job.autoconfigure.JobModuleProperties;
import cn.slainc.modules.job.entity.JobLogRecord;
import cn.slainc.modules.job.entity.JobTaskRecord;
import cn.slainc.modules.job.mapper.JobLogMapper;
import cn.slainc.modules.job.mapper.JobTaskMapper;
import cn.slainc.modules.job.support.CronSupport;
import cn.slainc.modules.job.support.NodeRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 定时任务中心服务：任务定义 CRUD、触发、执行日志、启动恢复；实现 JobRecorder 接收内核执行结果
public class JobManageService implements ApplicationRunner, JobRecorder {

    public static final String STATUS_RUNNING = "RUNNING";

    public static final String STATUS_PAUSED = "PAUSED";

    private static final Logger LOGGER = LoggerFactory.getLogger(JobManageService.class);

    private static final int MAX_NEXT_EXECUTIONS = 50;

    private static final int MAX_LOG_MESSAGE_LENGTH = 2000;

    private final JobTaskMapper jobTaskMapper;

    private final JobLogMapper jobLogMapper;

    private final JobScheduler jobScheduler;

    private final NodeRegistry nodeRegistry;

    private final JobModuleProperties properties;

    private final Map<String, JobHandler> handlers = new ConcurrentHashMap<>();

    public JobManageService(JobTaskMapper jobTaskMapper, JobLogMapper jobLogMapper, JobScheduler jobScheduler,
                            NodeRegistry nodeRegistry, JobModuleProperties properties,
                            Map<String, JobHandler> handlerBeans) {
        this.jobTaskMapper = jobTaskMapper;
        this.jobLogMapper = jobLogMapper;
        this.jobScheduler = jobScheduler;
        this.nodeRegistry = nodeRegistry;
        this.properties = properties;
        indexHandlers(handlerBeans);
    }

    public void registerHandler(String name, JobHandler handler) {
        if (name != null && !name.isBlank() && handler != null) {
            handlers.put(name, handler);
        }
    }

    public Map<String, JobHandler> registeredHandlers() {
        return Map.copyOf(handlers);
    }

    @Override
    public void run(ApplicationArguments args) {
        registerEnabledJobs();
    }

    public int registerEnabledJobs() {
        List<JobTaskRecord> jobs;
        try {
            jobs = jobTaskMapper.selectList(new LambdaQueryWrapper<JobTaskRecord>().eq(JobTaskRecord::getEnabled, true));
        } catch (RuntimeException e) {
            LOGGER.error("从 t_job_task 加载启用任务失败，本次未恢复任何调度；修复数据源后重启节点即可", e);
            return 0;
        }
        if (jobs == null || jobs.isEmpty()) {
            LOGGER.info("t_job_task 中没有启用状态的任务");
            return 0;
        }
        int registered = 0;
        for (JobTaskRecord job : jobs) {
            try {
                if (schedule(job, true)) {
                    registered++;
                }
            } catch (RuntimeException e) {
                LOGGER.error("恢复任务调度失败 jobName={}", job.getJobName(), e);
            }
        }
        LOGGER.info("已从 t_job_task 恢复 {} 个启用任务（共 {} 条）", registered, jobs.size());
        return registered;
    }

    public PageResult<JobTaskRecord> page(long pageNum, long pageSize, String keyword, String jobGroup) {
        LambdaQueryWrapper<JobTaskRecord> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(JobTaskRecord::getJobName, keyword);
        }
        if (jobGroup != null && !jobGroup.isBlank()) {
            wrapper.eq(JobTaskRecord::getJobGroup, jobGroup);
        }
        wrapper.orderByDesc(JobTaskRecord::getId);
        Page<JobTaskRecord> page = jobTaskMapper.selectPage(
                Page.of(normalizePageNum(pageNum), normalizePageSize(pageSize)), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public JobTaskRecord get(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "任务ID不能为空");
        }
        JobTaskRecord record = jobTaskMapper.selectById(id);
        if (record == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "任务不存在");
        }
        return record;
    }

    @Transactional(rollbackFor = Exception.class)
    public JobTaskRecord create(JobTaskRecord entity) {
        validate(entity);
        boolean enabled = entity.getEnabled() == null || entity.getEnabled();
        entity.setId(null);
        entity.setEnabled(enabled);
        entity.setStatus(statusOf(enabled));
        long now = System.currentTimeMillis();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (entity.getTenantId() == null) {
            entity.setTenantId(TenantContext.get());
        }
        jobTaskMapper.insert(entity);
        if (enabled) {
            afterCommit(() -> schedule(entity, false));
        }
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public JobTaskRecord update(JobTaskRecord entity) {
        if (entity == null || entity.getId() == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "任务ID不能为空");
        }
        validate(entity);
        JobTaskRecord exists = jobTaskMapper.selectById(entity.getId());
        if (exists == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "任务不存在");
        }
        String previousName = exists.getJobName();
        applyEditableFields(exists, entity);
        boolean enabled = exists.getEnabled() == null || exists.getEnabled();
        exists.setEnabled(enabled);
        exists.setStatus(statusOf(enabled));
        exists.setUpdateTime(System.currentTimeMillis());
        if (jobTaskMapper.updateById(exists) <= 0) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "任务不存在或已被删除");
        }
        afterCommit(() -> {
            jobScheduler.cancel(previousName);
            if (enabled) {
                schedule(exists, false);
            }
        });
        return exists;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        JobTaskRecord entity = id == null ? null : jobTaskMapper.selectById(id);
        if (entity == null) {
            return;
        }
        jobTaskMapper.deleteById(id);
        afterCommit(() -> jobScheduler.cancel(entity.getJobName()));
    }

    @Transactional(rollbackFor = Exception.class)
    public JobTaskRecord changeStatus(Long id, boolean enabled) {
        JobTaskRecord entity = id == null ? null : jobTaskMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "任务不存在");
        }
        boolean current = entity.getEnabled() == null || entity.getEnabled();
        if (current == enabled) {
            LOGGER.debug("任务状态未变化 id={} enabled={}", id, enabled);
            return entity;
        }
        entity.setEnabled(enabled);
        entity.setStatus(statusOf(enabled));
        entity.setUpdateTime(System.currentTimeMillis());
        if (jobTaskMapper.updateById(entity) <= 0) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "任务不存在或已被删除");
        }
        afterCommit(() -> {
            if (enabled) {
                schedule(entity, false);
            } else {
                jobScheduler.cancel(entity.getJobName());
            }
        });
        return entity;
    }

    public JobTriggerAck trigger(Long id, Map<String, Object> params) {
        JobTaskRecord entity = id == null ? null : jobTaskMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "任务不存在");
        }
        return jobScheduler.trigger(entity.getJobName(), params == null ? Map.of() : params);
    }

    public Map<String, Object> status(String jobName) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("jobName", jobName);
        status.put("definitions", jobScheduler.definitions().keySet());
        status.put("running", jobScheduler.runningJobs());
        status.put("handlers", handlers.keySet());
        status.put("lastResult", jobScheduler.lastResult(jobName));
        status.put("nodes", nodeRegistry.nodeStatus());
        return status;
    }

    public PageResult<JobLogRecord> logs(long pageNum, long pageSize, String jobName) {
        LambdaQueryWrapper<JobLogRecord> wrapper = new LambdaQueryWrapper<>();
        if (jobName != null && !jobName.isBlank()) {
            wrapper.eq(JobLogRecord::getJobName, jobName);
        }
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            wrapper.eq(JobLogRecord::getTenantId, tenantId);
        }
        wrapper.orderByDesc(JobLogRecord::getId);
        Page<JobLogRecord> page = jobLogMapper.selectPage(
                Page.of(normalizePageNum(pageNum), normalizePageSize(pageSize)), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public int purgeExpiredLogs() {
        var retention = properties.getLogRetention();
        if (retention == null || retention.isZero() || retention.isNegative()) {
            LOGGER.debug("任务日志保留策略未开启，无需清理");
            return 0;
        }
        long threshold = System.currentTimeMillis() - retention.toMillis();
        int deleted = jobLogMapper.delete(new LambdaQueryWrapper<JobLogRecord>()
                .lt(JobLogRecord::getFireTime, threshold));
        if (deleted > 0) {
            LOGGER.info("已清理 {} 条早于 {} 的任务日志", deleted, threshold);
        }
        return deleted;
    }

    public int deleteLogsByJob(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "任务名称不能为空");
        }
        int deleted = jobLogMapper.delete(new LambdaQueryWrapper<JobLogRecord>()
                .eq(JobLogRecord::getJobName, jobName));
        LOGGER.info("已按任务清理 {} 条任务日志 jobName={}", deleted, jobName);
        return deleted;
    }

    @Override
    public void record(JobContext context, JobResult result) {
        try {
            JobLogRecord record = new JobLogRecord();
            record.setJobName(context.jobName());
            record.setAttempt(context.attempt());
            record.setSuccess(result.success());
            record.setStatus(result.status() == null ? null : result.status().name());
            record.setMessage(truncate(result.message()));
            record.setTenantId(context.tenantId());
            record.setCostMillis(result.costMillis());
            record.setNodeId(nodeRegistry.nodeId());
            record.setFireTime(context.fireTime() == null ? System.currentTimeMillis()
                    : context.fireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            record.setCreateTime(System.currentTimeMillis());
            jobLogMapper.insert(record);
        } catch (RuntimeException e) {
            LOGGER.warn("任务日志落库失败 jobName={}", context.jobName(), e);
        }
    }

    public List<String> nextExecutions(String cron, int count) {
        int limited = Math.min(Math.max(count, 1), MAX_NEXT_EXECUTIONS);
        return CronSupport.nextExecutions(cron, limited).stream().map(Object::toString).toList();
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_LOG_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_LOG_MESSAGE_LENGTH) + "...";
    }

    private void indexHandlers(Map<String, JobHandler> handlerBeans) {
        if (handlerBeans == null || handlerBeans.isEmpty()) {
            LOGGER.warn("没有 JobHandler Bean，数据库配置的任务无法调度");
            return;
        }
        handlerBeans.forEach((beanName, handler) -> {
            Job annotation = AnnotatedElementUtils.findMergedAnnotation(AopUtils.getTargetClass(handler), Job.class);
            if (annotation != null && annotation.name() != null && !annotation.name().isBlank()) {
                handlers.put(annotation.name(), handler);
            }
            if (beanName != null && !beanName.isBlank()) {
                handlers.put(beanName, handler);
            }
        });
        LOGGER.info("已索引 {} 个任务处理器名称：{}", handlers.size(), handlers.keySet());
    }

    private boolean schedule(JobTaskRecord entity, boolean startup) {
        JobHandler handler = handlers.get(entity.getHandlerName());
        if (handler == null) {
            LOGGER.warn("任务处理器不存在 jobName={} handlerName={}", entity.getJobName(), entity.getHandlerName());
            return false;
        }
        JobDefinition definition = toDefinition(entity, handler);
        if (startup) {
            jobScheduler.registerOnStartup(definition);
        } else {
            jobScheduler.register(definition);
        }
        LOGGER.info("已注册任务 jobName={} handlerName={} cron={} startup={}",
                definition.name(), entity.getHandlerName(), definition.cron(), startup);
        return true;
    }

    private JobDefinition toDefinition(JobTaskRecord entity, JobHandler handler) {
        return new JobDefinition(entity.getJobName(), trimToNull(entity.getCron()),
                entity.getRemark(),
                entity.getRetryCount() == null ? 0 : Math.max(entity.getRetryCount(), 0),
                entity.retryIntervalDuration(),
                entity.timeoutDuration(),
                entity.concurrentOrTrue(),
                MisfirePolicy.of(entity.getMisfirePolicy(), MisfirePolicy.SKIP),
                handler,
                entity.getTenantId());
    }

    private void applyEditableFields(JobTaskRecord target, JobTaskRecord source) {
        target.setJobName(source.getJobName());
        target.setHandlerName(source.getHandlerName());
        target.setCron(source.getCron());
        target.setParams(source.getParams());
        if (source.getEnabled() != null) {
            target.setEnabled(source.getEnabled());
        }
        target.setRemark(source.getRemark());
        target.setRetryCount(source.getRetryCount());
        target.setRetryInterval(source.getRetryInterval());
        target.setTimeout(source.getTimeout());
        target.setConcurrent(source.getConcurrent());
        target.setMisfirePolicy(source.getMisfirePolicy());
        target.setJobGroup(source.getJobGroup());
        target.setTenantId(source.getTenantId() == null ? target.getTenantId() : source.getTenantId());
    }

    private void validate(JobTaskRecord entity) {
        if (entity == null || entity.getJobName() == null || entity.getJobName().isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "任务名称不能为空");
        }
        if (entity.getHandlerName() == null || entity.getHandlerName().isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "处理器名称不能为空");
        }
        if (entity.getCron() != null && !entity.getCron().isBlank() && !CronSupport.isValid(entity.getCron())) {
            throw new BizException(ResultCode.BAD_REQUEST, "Cron 表达式不合法");
        }
        if (entity.getMisfirePolicy() != null && !entity.getMisfirePolicy().isBlank()
                && MisfirePolicy.of(entity.getMisfirePolicy()) == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "错过触发策略不合法，可选值：SKIP / FIRE_ONCE_NOW");
        }
    }

    private void afterCommit(Runnable action) {
        // 事务内注册提交后回调，事务外直接执行
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private long normalizePageNum(long pageNum) {
        return Math.max(pageNum, 1L);
    }

    private long normalizePageSize(long pageSize) {
        return Math.min(Math.max(pageSize, 1L), properties.getMaxPageSize());
    }

    private static String statusOf(boolean enabled) {
        return enabled ? STATUS_RUNNING : STATUS_PAUSED;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
