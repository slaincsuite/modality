package cn.slainc.tenant.resolver;

import cn.dev33.satoken.stp.StpUtil;
import cn.slainc.core.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionTenantResolver implements TenantResolver {

    public static final int ORDER = 100;

    public static final String DEFAULT_ATTRIBUTE = TenantContext.SESSION_ATTRIBUTE;

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
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            Object value = StpUtil.getSession().get(attribute);
            return TenantIds.parse(value == null ? null : String.valueOf(value), lookup);
        } catch (RuntimeException exception) {
            // 过滤器链早于 Sa-Token 上下文时就绪时会抛异常，此时按无会话租户处理
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
