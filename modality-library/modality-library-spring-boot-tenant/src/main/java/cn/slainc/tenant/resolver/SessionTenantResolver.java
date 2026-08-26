package cn.slainc.tenant.resolver;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionTenantResolver implements TenantResolver {

    public static final int ORDER = 500;

    public static final String DEFAULT_ATTRIBUTE = "tenantId";

    private static final Logger log = LoggerFactory.getLogger(SessionTenantResolver.class);

    private final String attribute;

    private final TenantLookup lookup;

    public SessionTenantResolver(TenantLookup lookup) {
        this(DEFAULT_ATTRIBUTE, lookup);
    }

    public SessionTenantResolver(String attribute, TenantLookup lookup) {
        this.attribute = attribute == null || attribute.isBlank() ? DEFAULT_ATTRIBUTE : attribute;
        this.lookup = lookup;
    }

    @Override
    public Long resolve(HttpServletRequest request) {
        if (!StpUtil.isLogin()) {
            return null;
        }
        try {
            Object value = StpUtil.getSession().get(attribute);
            return TenantIds.parse(value == null ? null : String.valueOf(value), lookup);
        } catch (RuntimeException exception) {
            log.debug("从会话解析租户失败：{}", exception.getMessage());
            return null;
        }
    }

    @Override
    public int order() {
        return ORDER;
    }

    public String attribute() {
        return attribute;
    }
}
