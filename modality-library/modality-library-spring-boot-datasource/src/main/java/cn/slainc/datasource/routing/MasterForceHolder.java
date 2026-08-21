package cn.slainc.datasource.routing;

import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class MasterForceHolder {

    public static final long DEFAULT_MILLIS = 3000L;

    private static final ThreadLocal<Long> EXPIRE_AT = new ThreadLocal<>();

    private MasterForceHolder() {
    }

    public static void force() {
        force(DEFAULT_MILLIS);
    }

    public static void force(long millis) {
        EXPIRE_AT.set(System.currentTimeMillis() + Math.max(millis, 1L));
    }

    public static boolean isForced() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return true;
        }
        Long expireAt = EXPIRE_AT.get();
        if (expireAt == null) {
            return false;
        }
        if (System.currentTimeMillis() > expireAt) {
            EXPIRE_AT.remove();
            return false;
        }
        return true;
    }

    public static void clear() {
        EXPIRE_AT.remove();
    }
}
