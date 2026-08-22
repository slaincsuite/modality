package cn.slainc.mybatis.sql;

import cn.slainc.mybatis.properties.MybatisProperties;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class SqlLogInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlLogInterceptor.class);

    private static final Logger auditLog = LoggerFactory.getLogger("modality.sql.audit");

    private final MybatisProperties properties;

    private final SqlStatistics statistics;

    private final ApplicationEventPublisher eventPublisher;

    private final ThreadPoolExecutor logger;

    public SqlLogInterceptor(MybatisProperties properties, SqlStatistics statistics,
                             ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.statistics = statistics;
        this.eventPublisher = eventPublisher;
        this.logger = createExecutor(properties.getSqlLog().getQueueCapacity());
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.getSqlLog().isEnabled()) {
            return invocation.proceed();
        }
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        BoundSql boundSql = resolveBoundSql(invocation, statement);
        long start = System.nanoTime();
        try {
            Object result = invocation.proceed();
            publish(statement, boundSql, result, elapsed(start), null);
            return result;
        } catch (Throwable throwable) {
            publish(statement, boundSql, null, elapsed(start), throwable);
            throw throwable;
        }
    }

    private BoundSql resolveBoundSql(Invocation invocation, MappedStatement statement) {
        Object[] args = invocation.getArgs();
        if (args.length == 6 && args[5] instanceof BoundSql boundSql) {
            return boundSql;
        }
        try {
            return statement.getBoundSql(args.length > 1 ? args[1] : null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void publish(MappedStatement statement, BoundSql boundSql, Object result,
                         long elapsedMillis, Throwable throwable) {
        String commandType = statement.getSqlCommandType().name();
        boolean slow = elapsedMillis >= properties.getSqlLog().getSlowThreshold();
        SlowSqlRecord slowRecord = slow ? new SlowSqlRecord(statement.getId(),
                boundSql == null ? null : singleLine(boundSql.getSql()), elapsedMillis, LocalDateTime.now()) : null;
        statistics.record(commandType, elapsedMillis, slowRecord);
        if (throwable != null) {
            dispatch(() -> logFailure(statement, boundSql, elapsedMillis, throwable));
            return;
        }
        boolean audited = properties.getSqlLog().isAudit() && !"SELECT".equals(commandType);
        boolean sampled = !slow && !audited && sampleHit();
        if (!slow && !audited && !sampled) {
            return;
        }
        dispatch(() -> logExecution(statement, boundSql, result, elapsedMillis, slow, audited, commandType));
    }

    private void logExecution(MappedStatement statement, BoundSql boundSql, Object result, long elapsedMillis,
                              boolean slow, boolean audited, String commandType) {
        String sql = boundSql == null ? null : SqlParameterFormatter.format(statement.getConfiguration(), boundSql);
        String rendered = properties.getSqlLog().isPrintResult() && result != null
                ? sql + " | 结果: " + SqlResultFormatter.format(result, properties.getSqlLog().getMaxResultRows()) : sql;
        if (slow) {
            log.warn("慢 SQL 耗时 {} 毫秒 [{}] {}: {}", elapsedMillis, commandType, statement.getId(), rendered);
        } else {
            log.info("SQL 耗时 {} 毫秒 [{}] {}: {}", elapsedMillis, commandType, statement.getId(), rendered);
        }
        if (audited) {
            auditLog.info("[{}] {} 耗时 {} 毫秒: {}", commandType, statement.getId(), elapsedMillis, rendered);
            eventPublisher.publishEvent(new SqlAuditEvent(statement.getId(), commandType, sql, null,
                    elapsedMillis, affectedRows(result), true, null, MDC.get("traceId"), LocalDateTime.now()));
        }
    }

    private void logFailure(MappedStatement statement, BoundSql boundSql, long elapsedMillis, Throwable throwable) {
        String sql = boundSql == null ? null : SqlParameterFormatter.format(statement.getConfiguration(), boundSql);
        log.error("SQL 执行失败，耗时 {} 毫秒 {}: {}", elapsedMillis, statement.getId(), sql, throwable);
        if (properties.getSqlLog().isAudit()) {
            eventPublisher.publishEvent(new SqlAuditEvent(statement.getId(),
                    statement.getSqlCommandType().name(), sql, null, elapsedMillis, 0, false,
                    throwable.getMessage(), MDC.get("traceId"), LocalDateTime.now()));
        }
    }

    private boolean sampleHit() {
        double rate = properties.getSqlLog().getSampleRate();
        return rate > 0 && ThreadLocalRandom.current().nextDouble() < rate;
    }

    private void dispatch(Runnable task) {
        if (!properties.getSqlLog().isAsync()) {
            task.run();
            return;
        }
        logger.execute(task);
    }

    private static int affectedRows(Object result) {
        return result instanceof Integer value ? value : 0;
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static String singleLine(String sql) {
        return sql == null ? null : sql.replaceAll("\\s+", " ").trim();
    }

    private static ThreadPoolExecutor createExecutor(int capacity) {
        AtomicInteger counter = new AtomicInteger(1);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(capacity, 64)),
                runnable -> {
                    Thread thread = new Thread(runnable, "modality-sql-log-" + counter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public void shutdown() {
        logger.shutdownNow();
    }
}
