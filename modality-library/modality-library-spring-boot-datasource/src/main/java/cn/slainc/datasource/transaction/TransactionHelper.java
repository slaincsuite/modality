package cn.slainc.datasource.transaction;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class TransactionHelper {

    private final PlatformTransactionManager transactionManager;

    public TransactionHelper(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public <T> T execute(TransactionCallback<T> action) {
        return template(false, -1).execute(action);
    }

    public void executeWithoutResult(Consumer<TransactionStatus> action) {
        template(false, -1).executeWithoutResult(action);
    }

    public <T> T executeReadOnly(Supplier<T> supplier) {
        return template(true, -1).execute(status -> supplier.get());
    }

    public <T> T executeWithTimeout(int timeoutSeconds, Supplier<T> supplier) {
        return template(false, timeoutSeconds).execute(status -> supplier.get());
    }

    public <T> T executeOn(String dataSourceName, Supplier<T> supplier) {
        DynamicDataSourceContextHolder.push(dataSourceName);
        try {
            return template(false, -1).execute(status -> supplier.get());
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    public void runOn(String dataSourceName, Runnable runnable) {
        executeOn(dataSourceName, () -> {
            runnable.run();
            return null;
        });
    }

    private TransactionTemplate template(boolean readOnly, int timeoutSeconds) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(readOnly);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        if (timeoutSeconds > 0) {
            template.setTimeout(timeoutSeconds);
        }
        return template;
    }
}
