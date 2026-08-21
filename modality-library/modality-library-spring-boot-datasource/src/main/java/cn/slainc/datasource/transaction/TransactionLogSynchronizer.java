package cn.slainc.datasource.transaction;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class TransactionLogSynchronizer implements TransactionSynchronization {

    private static final Logger log = LoggerFactory.getLogger(TransactionLogSynchronizer.class);

    private final String description;

    private final long startedAt = System.currentTimeMillis();

    private final String dataSourceName;

    private TransactionLogSynchronizer(String description) {
        this.description = description;
        this.dataSourceName = DynamicDataSourceContextHolder.peek();
    }

    public static void record(String description) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionLogSynchronizer(description));
        }
    }

    @Override
    public void afterCompletion(int status) {
        long elapsed = System.currentTimeMillis() - startedAt;
        String result = status == TransactionSynchronization.STATUS_COMMITTED ? "已提交" : "已回滚";
        log.info("事务 {} {}，数据源 [{}]，耗时 {} 毫秒", description, result,
                dataSourceName == null ? "默认" : dataSourceName, elapsed);
    }
}
