package cn.slainc.logger.oplog;

import java.lang.reflect.Method;

public interface AuditSnapshotProvider {

    Object before(Object target, Method method, Object[] args);

    default Object after(Object target, Method method, Object[] args, Object result) {
        return null;
    }

    default boolean supports(Method method) {
        return true;
    }

    default int order() {
        return 0;
    }
}
