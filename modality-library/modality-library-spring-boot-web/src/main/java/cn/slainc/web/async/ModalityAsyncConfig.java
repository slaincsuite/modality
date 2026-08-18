package cn.slainc.web.async;

import cn.slainc.core.context.ContextTaskDecorator;
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

public class ModalityAsyncConfig implements WebMvcConfigurer {

    private final AsyncProperties properties;
    private final ExecutorService executor;

    public ModalityAsyncConfig(AsyncProperties properties) {
        this.properties = properties;
        this.executor = createExecutor(properties);
    }

    private static ExecutorService createExecutor(AsyncProperties properties) {
        if (properties.isVirtualThreads()) {
            return Executors.newThreadPerTaskExecutor(decoratingFactory("modality-async-", true));
        }
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                properties.getCorePoolSize(),
                properties.getCorePoolSize(),
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(properties.getQueueCapacity()),
                decoratingFactory("modality-async-", false),
                new ThreadPoolExecutor.CallerRunsPolicy());
        pool.allowCoreThreadTimeOut(false);
        return pool;
    }

    private static ThreadFactory decoratingFactory(String prefix, boolean virtual) {
        TaskDecorator decorator = new ContextTaskDecorator();
        return runnable -> {
            Runnable decorated = decorator.decorate(runnable);
            return virtual
                    ? Thread.ofVirtual().name(prefix, 0).unstarted(decorated)
                    : Thread.ofPlatform().daemon(true).name(prefix, 0).unstarted(decorated);
        };
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
}
