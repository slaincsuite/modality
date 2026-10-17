package cn.slainc.services.livemonitor;

import cn.dev33.satoken.stp.StpInterface;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.result.Result;
import cn.slainc.security.StpKit;
import cn.slainc.web.filter.FilterOrder;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@SpringBootConfiguration
@EnableAutoConfiguration
public class LiveMonitorModuleApplication {

    public static final long FULL_PERMISSION_USER_ID = 91001L;

    public static final long NO_PERMISSION_USER_ID = 91002L;

    @RestController
    public static class TestLoginController {

        @GetMapping("/live-monitor-test/login")
        public Result<String> login(@RequestParam long userId) {
            StpKit.tenantUser().login(userId);
            return Result.ok(StpKit.tenantUser().getTokenValue());
        }
    }

    @Bean
    public StpInterface liveMonitorTestPermissionProvider() {
        return new StpInterface() {

            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                if (String.valueOf(NO_PERMISSION_USER_ID).equals(String.valueOf(loginId))) {
                    return List.of();
                }
                return List.of(LiveMonitorKeys.PERMISSION_STREAM_QUERY, LiveMonitorKeys.PERMISSION_STREAM_CREATE,
                        LiveMonitorKeys.PERMISSION_STREAM_UPDATE, LiveMonitorKeys.PERMISSION_STREAM_DELETE,
                        LiveMonitorKeys.PERMISSION_STREAM_CHECK);
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return List.of();
            }
        };
    }

    @Bean
    public FilterRegistrationBean<Filter> liveMonitorTestTenantFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(new Filter() {

            @Override
            public void doFilter(ServletRequest request, ServletResponse response, jakarta.servlet.FilterChain chain)
                    throws IOException, jakarta.servlet.ServletException {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                String tenantId = httpRequest.getHeader("X-Tenant-Id");
                try {
                    if (tenantId != null && !tenantId.isBlank()) {
                        TenantContext.set(Long.parseLong(tenantId));
                    }
                    chain.doFilter(request, response);
                } finally {
                    TenantContext.clear();
                }
            }
        });
        registration.setOrder(FilterOrder.TENANT);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
