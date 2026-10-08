package cn.slainc.web.async;

import cn.slainc.core.context.ContextTaskDecorator;
import cn.slainc.utils.thread.ThreadPoolUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncConfig implements WebMvcConfigurer, AutoCloseable {

    private final AsyncProperties properties;
    private final ExecutorService executor;

    public AsyncConfig(AsyncProperties properties) {
        this.properties = properties;
        this.executor = createExecutor(properties);
    }

    private static ExecutorService createExecutor(AsyncProperties properties) {
        if (properties.isVirtualThreads()) {
            return Executors.newThreadPerTaskExecutor(decoratingFactory("modality-async", true));
        }
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                properties.getCorePoolSize(),
                properties.getCorePoolSize(),
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(properties.getQueueCapacity()),
                decoratingFactory("modality-async", false),
                new ThreadPoolExecutor.CallerRunsPolicy());
        pool.allowCoreThreadTimeOut(false);
        return pool;
    }

    private static ThreadFactory decoratingFactory(String threadName, boolean virtual) {
        TaskDecorator decorator = new ContextTaskDecorator();
        ThreadFactory factory = virtual ? ThreadPoolUtils.namedThreadFactory(threadName, true)
                : ThreadPoolUtils.fixedNameThreadFactory(threadName);
        return runnable -> factory.newThread(decorator.decorate(runnable));
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        AsyncTaskExecutor taskExecutor = new TaskExecutorAdapter(executor);
        configurer.setTaskExecutor(taskExecutor);
        configurer.setDefaultTimeout(properties.getTimeoutMillis());
    }

    public ExecutorService executor() {
        return executor;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
