package cn.slainc.changeaudit.support;

import cn.slainc.changeaudit.spi.ChangeRecord;
import cn.slainc.changeaudit.spi.ChangeRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

public class AsyncChangeRecorder implements ChangeRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncChangeRecorder.class);

    private final ChangeRecorder delegate;

    private final Executor executor;

    public AsyncChangeRecorder(ChangeRecorder delegate, Executor executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    @Override
    public void record(ChangeRecord change) {
        if (change == null || delegate == null) {
            return;
        }
        if (executor == null) {
            delegate.record(change);
            return;
        }
        try {
            executor.execute(() -> delegate.record(change));
        } catch (RuntimeException e) {
            LOGGER.warn("派发变更审计任务失败，改为同步写入: {}", e.getMessage());
            delegate.record(change);
        }
    }
}
