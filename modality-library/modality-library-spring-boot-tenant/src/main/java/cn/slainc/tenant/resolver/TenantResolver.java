package cn.slainc.tenant.resolver;

import jakarta.servlet.http.HttpServletRequest;

public interface TenantResolver {

    Long resolve(HttpServletRequest request);

    int order();

    default String name() {
        return getClass().getSimpleName();
    }
}
