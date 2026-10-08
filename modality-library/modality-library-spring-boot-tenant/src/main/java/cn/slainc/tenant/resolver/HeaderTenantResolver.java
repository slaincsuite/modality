package cn.slainc.tenant.resolver;

import cn.slainc.core.constant.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;

public class HeaderTenantResolver implements TenantResolver {

    public static final int ORDER = 900;

    private final String headerName;

    private final TenantLookup lookup;

    public HeaderTenantResolver(TenantLookup lookup) {
        this(HttpHeaders.TENANT_ID, lookup);
    }

    public HeaderTenantResolver(String headerName, TenantLookup lookup) {
        this.headerName = headerName == null || headerName.isBlank() ? HttpHeaders.TENANT_ID : headerName;
        this.lookup = lookup;
    }

    @Override
    public Long resolve(HttpServletRequest request) {
        return request == null ? null : TenantIds.parse(request.getHeader(headerName), lookup);
    }

    @Override
    public int order() {
        return ORDER;
    }

    public String headerName() {
        return headerName;
    }
}
