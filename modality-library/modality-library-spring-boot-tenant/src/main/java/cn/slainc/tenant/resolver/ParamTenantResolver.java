package cn.slainc.tenant.resolver;

import jakarta.servlet.http.HttpServletRequest;

public class ParamTenantResolver implements TenantResolver {

    public static final int ORDER = 400;

    public static final String DEFAULT_PARAM = "tenantId";

    private final String paramName;

    private final TenantLookup lookup;

    public ParamTenantResolver(TenantLookup lookup) {
        this(DEFAULT_PARAM, lookup);
    }

    public ParamTenantResolver(String paramName, TenantLookup lookup) {
        this.paramName = paramName == null || paramName.isBlank() ? DEFAULT_PARAM : paramName;
        this.lookup = lookup;
    }

    @Override
    public Long resolve(HttpServletRequest request) {
        return request == null ? null : TenantIds.parse(request.getParameter(paramName), lookup);
    }

    @Override
    public int order() {
        return ORDER;
    }

    public String paramName() {
        return paramName;
    }
}
