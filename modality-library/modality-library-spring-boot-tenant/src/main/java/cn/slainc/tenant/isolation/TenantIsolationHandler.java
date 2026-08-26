package cn.slainc.tenant.isolation;

public interface TenantIsolationHandler {

    boolean supports(TenantIsolationStrategy strategy);

    void apply(TenantInfo tenant);

    void remove(TenantInfo tenant);

    default int order() {
        return 100;
    }

    default String describe() {
        return getClass().getSimpleName();
    }
}
