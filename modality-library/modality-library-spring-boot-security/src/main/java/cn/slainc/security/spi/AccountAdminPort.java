package cn.slainc.security.spi;

public interface AccountAdminPort {

    Long tenantIdOf(Long userId);

    void delete(Long userId);
}
