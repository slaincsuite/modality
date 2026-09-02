package cn.slainc.logger.logback;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.mask.LogMasker;
import cn.slainc.logger.monitor.LogAlertDispatcher;
import cn.slainc.logger.monitor.LogMetrics;
import cn.slainc.logger.monitor.MetricsAppender;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LogbackConfigurer implements InitializingBean, DisposableBean {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LogbackConfigurer.class);

    public static final String FILE_APPENDER_NAME = "modalityJsonFile";

    public static final String ASYNC_APPENDER_NAME = "modalityJsonAsync";

    public static final String METRICS_APPENDER_NAME = "modalityMetrics";

    private final LoggerProperties properties;

    private final LogMasker masker;

    private final LogMetrics metrics;

    private final LogAlertDispatcher alertDispatcher;

    private volatile Appender<ILoggingEvent> jsonAppender;

    private volatile Appender<ILoggingEvent> metricsAppender;

    public LogbackConfigurer(LoggerProperties properties, LogMasker masker, LogMetrics metrics,
                             LogAlertDispatcher alertDispatcher) {
        this.properties = properties;
        this.masker = masker;
        this.metrics = metrics;
        this.alertDispatcher = alertDispatcher;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.isEnabled() || !properties.getJson().isEnabled()) {
            return;
        }
        try {
            configure();
        } catch (RuntimeException ex) {
            log.warn("JSON 日志装配失败，已回退到宿主原有日志配置：{}", ex.getMessage(), ex);
        }
    }

    public synchronized void configure() {
        LoggerContext context = LogbackLevelManager.currentContext();
        if (context == null) {
            log.warn("当前日志实现不是 Logback，跳过 JSON 日志装配");
            return;
        }
        if (jsonAppender != null) {
            return;
        }
        Appender<ILoggingEvent> fileAppender = buildFileAppender(context);
        this.jsonAppender = properties.getAsync().isEnabled() ? wrapAsync(context, fileAppender) : fileAppender;
        this.metricsAppender = buildMetricsAppender(context);
        if (properties.getJson().isAttachToRoot()) {
            attachToRoot(context);
        }
    }

    public Appender<ILoggingEvent> jsonAppender() {
        return jsonAppender;
    }

    public Appender<ILoggingEvent> metricsAppender() {
        return metricsAppender;
    }

    public Path logDirectory() {
        return Path.of(properties.getOutput().getDir()).toAbsolutePath().normalize();
    }

    public Path logFile() {
        return logDirectory().resolve(properties.getOutput().getFileName());
    }

    private Appender<ILoggingEvent> buildFileAppender(LoggerContext context) {
        LoggerProperties.Output output = properties.getOutput();
        Path directory = logDirectory();
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("创建日志目录失败：" + directory, ex);
        }
        JsonLogEncoder encoder = new JsonLogEncoder(properties, masker);
        encoder.setContext(context);
        encoder.start();

        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setContext(context);
        appender.setName(FILE_APPENDER_NAME);
        appender.setFile(directory.resolve(output.getFileName()).toString());
        appender.setAppend(output.isAppend());
        appender.setImmediateFlush(output.isImmediateFlush());
        appender.setEncoder(encoder);

        SizeAndTimeBasedRollingPolicy<ILoggingEvent> policy = new SizeAndTimeBasedRollingPolicy<>();
        policy.setContext(context);
        policy.setParent(appender);
        policy.setFileNamePattern(directory.resolve(output.getRollingPattern()).toString());
        policy.setMaxFileSize(FileSize.valueOf(output.getMaxFileSize()));
        policy.setMaxHistory(output.getMaxHistoryDays());
        policy.setTotalSizeCap(FileSize.valueOf(output.getTotalSizeCap()));
        policy.setCleanHistoryOnStart(true);
        policy.start();
        appender.setRollingPolicy(policy);
        appender.start();
        return appender;
    }

    private Appender<ILoggingEvent> wrapAsync(LoggerContext context, Appender<ILoggingEvent> delegate) {
        LoggerProperties.Async async = properties.getAsync();
        AsyncAppender appender = new AsyncAppender();
        appender.setContext(context);
        appender.setName(ASYNC_APPENDER_NAME);
        appender.setQueueSize(async.getQueueSize());
        appender.setDiscardingThreshold(async.getDiscardingThreshold());
        appender.setNeverBlock(async.isNeverBlock());
        appender.setMaxFlushTime(async.getMaxFlushTimeMillis());
        appender.setIncludeCallerData(async.isIncludeCallerData());
        appender.addAppender(delegate);
        appender.start();
        return appender;
    }

    private Appender<ILoggingEvent> buildMetricsAppender(LoggerContext context) {
        if (metrics == null || !properties.getMonitor().isEnabled()) {
            return null;
        }
        MetricsAppender appender = new MetricsAppender(metrics, alertDispatcher);
        appender.setContext(context);
        appender.setName(METRICS_APPENDER_NAME);
        appender.start();
        return appender;
    }

    private void attachToRoot(LoggerContext context) {
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (properties.getJson().isReplaceRootAppenders()) {
            root.detachAndStopAllAppenders();
        }
        root.addAppender(jsonAppender);
        if (metricsAppender != null) {
            root.addAppender(metricsAppender);
        }
    }

    @Override
    public synchronized void destroy() {
        LoggerContext context = LogbackLevelManager.currentContext();
        if (context != null) {
            Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            if (jsonAppender != null) {
                root.detachAppender(jsonAppender);
            }
            if (metricsAppender != null) {
                root.detachAppender(metricsAppender);
            }
        }
        // AsyncAppender 的 stop 会先排干队列再停下游 appender，因此这里不能反过来先停文件 appender
        stopQuietly(jsonAppender);
        stopQuietly(metricsAppender);
        jsonAppender = null;
        metricsAppender = null;
    }

    private void stopQuietly(Appender<ILoggingEvent> appender) {
        if (appender == null) {
            return;
        }
        try {
            appender.stop();
        } catch (RuntimeException ex) {
            log.warn("停止日志 appender 失败：{}", appender.getName(), ex);
        }
    }
}
