package cn.slainc.logger.audit;

import cn.slainc.core.context.ContextTaskDecorator;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;

public class AuditLogDispatcher implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AuditLogDispatcher.class);

    private final List<AuditLogStore> stores;

    private final ThreadPoolTaskExecutor executor;

    private final LongAdder dispatched = new LongAdder();

    private final LongAdder failed = new LongAdder();

    private final LongAdder rejected = new LongAdder();

    public AuditLogDispatcher(LoggerProperties.Audit properties, List<AuditLogStore> stores) {
        List<AuditLogStore> sorted = stores == null ? new ArrayList<>() : new ArrayList<>(stores);
        sorted.sort((left, right) -> Integer.compare(left.order(), right.order()));
        this.stores = List.copyOf(sorted);
        if (properties.isAsync()) {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setThreadNamePrefix("modality-audit-");
            pool.setCorePoolSize(properties.getCorePoolSize());
            pool.setMaxPoolSize(properties.getMaxPoolSize());
            pool.setQueueCapacity(properties.getQueueCapacity());
            pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
            pool.setTaskDecorator(new ContextTaskDecorator());
            pool.setWaitForTasksToCompleteOnShutdown(true);
            pool.setAwaitTerminationSeconds(5);
            pool.initialize();
            this.executor = pool;
        } else {
            this.executor = null;
        }
    }

    public void dispatch(AuditRecord record) {
        if (record == null || stores.isEmpty()) {
            return;
        }
        if (executor == null) {
            save(record);
            return;
        }
        try {
            executor.execute(() -> save(record));
        } catch (RejectedExecutionException ex) {
            // 审计数据不允许丢，队列满时降级为同步落库
            rejected.increment();
            save(record);
        }
    }

    public int storeCount() {
        return stores.size();
    }

    public long dispatchedCount() {
        return dispatched.sum();
    }

    public long failedCount() {
        return failed.sum();
    }

    public long rejectedCount() {
        return rejected.sum();
    }

    private void save(AuditRecord record) {
        for (AuditLogStore store : stores) {
            try {
                store.save(record);
            } catch (RuntimeException ex) {
                failed.increment();
                log.warn("审计日志写入失败：{}", store.name(), ex);
            }
        }
        dispatched.increment();
    }

    @Override
    public void destroy() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
