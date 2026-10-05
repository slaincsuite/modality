package cn.slainc.modules.audit.writer;

import cn.slainc.modules.audit.entity.ApiLogRecord;
import cn.slainc.modules.audit.mapper.ApiLogMapper;
import cn.slainc.utils.thread.ThreadPoolUtils;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;


// API 访问日志异步写入器：daemon 单线程 + 有界队列；队列满时丢弃并计数（可采样数据允许丢，不阻塞业务线程）
public class ApiLogWriter implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ApiLogWriter.class);

    private static final long WARN_EVERY_DROPPED = 1000;

    private final ApiLogMapper apiLogMapper;

    private final ThreadPoolExecutor executor;

    private final AtomicLong dropped = new AtomicLong();

    public ApiLogWriter(ApiLogMapper apiLogMapper, int queueCapacity) {
        this.apiLogMapper = apiLogMapper;
        this.executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(Math.max(100, queueCapacity)),
                ThreadPoolUtils.fixedNameThreadFactory("modality-audit-api-log"),
                (runnable, pool) -> warnDropped());
        this.executor.allowCoreThreadTimeOut(true);
    }

    public void submit(ApiLogRecord record) {
        if (record == null) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    apiLogMapper.insert(record);
                } catch (Exception e) {
                    log.warn("API 访问日志落库失败: {} {}", e.getClass().getName(), e.getMessage());
                }
            });
        } catch (Exception e) {
            warnDropped();
        }
    }

    public long droppedCount() {
        return dropped.get();
    }

    private void warnDropped() {
        long total = dropped.incrementAndGet();
        if (total % WARN_EVERY_DROPPED == 1) {
            log.warn("API 访问日志写入队列已满，累计丢弃 {} 条", total);
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("API 访问日志写入器关闭超时，剩余任务被丢弃");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
