package cn.slainc.datasource.transaction;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

public class TransactionHelper {

    private final PlatformTransactionManager transactionManager;

    public TransactionHelper(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public <T> T executeOn(String dataSourceName, Supplier<T> supplier) {
        DynamicDataSourceContextHolder.push(dataSourceName);
        try {
            return template(false, -1).execute(status -> supplier.get());
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
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
