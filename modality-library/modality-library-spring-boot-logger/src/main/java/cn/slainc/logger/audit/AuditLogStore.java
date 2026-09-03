package cn.slainc.logger.audit;

public interface AuditLogStore {

    void save(AuditRecord record);

    default String name() {
        return getClass().getSimpleName();
    }

    default int order() {
        return 0;
    }
}
