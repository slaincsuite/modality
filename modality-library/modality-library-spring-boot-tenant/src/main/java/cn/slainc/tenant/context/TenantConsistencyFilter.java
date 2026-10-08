package cn.slainc.tenant.context;

import cn.slainc.core.context.TenantContext;
import cn.slainc.tenant.resolver.TenantResolver;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class TenantConsistencyFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TenantConsistencyFilter.class);

    private final TenantResolver sessionResolver;

    private final List<TenantResolver> claimResolvers;

    public TenantConsistencyFilter(TenantResolver sessionResolver, List<TenantResolver> claimResolvers) {
        this.sessionResolver = sessionResolver;
        this.claimResolvers = claimResolvers == null ? List.of() : List.copyOf(claimResolvers);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            Long sessionTenant = resolve(sessionResolver, httpRequest);
            if (sessionTenant != null) {
                Long claimed = firstClaim(httpRequest);
                if (claimed != null && !claimed.equals(sessionTenant)) {
                    log.warn("请求声明的租户 [{}] 与登录会话租户 [{}] 不一致", claimed, sessionTenant);
                    TenantRejections.forbidden(response, "请求租户与登录会话租户不一致");
                    return;
                }
                TenantContext.set(sessionTenant);
            }
        }
        chain.doFilter(request, response);
    }

    private Long firstClaim(HttpServletRequest request) {
        for (TenantResolver resolver : claimResolvers) {
            Long tenantId = resolve(resolver, request);
            if (tenantId != null) {
                return tenantId;
            }
        }
        return null;
    }

    private static Long resolve(TenantResolver resolver, HttpServletRequest request) {
        if (resolver == null) {
            return null;
        }
        try {
            return resolver.resolve(request);
        } catch (RuntimeException exception) {
            log.debug("租户解析失败：{}", exception.getMessage());
            return null;
        }
    }
}
