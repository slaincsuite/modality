package cn.slainc.core.context;

import org.springframework.core.task.TaskDecorator;

public class ContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        ContextSnapshot callerContext = ContextSnapshot.capture();
        return () -> {
            ContextSnapshot workerContext = ContextSnapshot.capture();
            try {
                ContextSnapshot.restoreAll(callerContext);
                runnable.run();
            } finally {
                ContextSnapshot.restoreAll(workerContext);
            }
        };
    }
}
