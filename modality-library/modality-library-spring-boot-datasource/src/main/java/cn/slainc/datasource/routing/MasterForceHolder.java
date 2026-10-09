package cn.slainc.datasource.routing;

import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class MasterForceHolder {

    private MasterForceHolder() {
    }

    public static boolean isForced() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }
}
