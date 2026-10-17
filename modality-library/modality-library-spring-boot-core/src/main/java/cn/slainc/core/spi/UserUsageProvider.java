package cn.slainc.core.spi;

public interface UserUsageProvider {

    UserUsage usage(Long tenantId);
}
