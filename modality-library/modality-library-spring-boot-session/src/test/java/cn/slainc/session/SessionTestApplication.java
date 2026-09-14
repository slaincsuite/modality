package cn.slainc.session;

import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.web.websocket.WebSocketBroadcaster;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.Filter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
@EnableAutoConfiguration
public class SessionTestApplication {

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    public RecordingSessionKickNotifier recordingSessionKickNotifier() {
        return new RecordingSessionKickNotifier();
    }

    @Bean
    public WebSocketBroadcaster webSocketBroadcaster() {
        return new FakeWebSocketBroadcaster();
    }

    @Bean
    public TestEventCollector testEventCollector() {
        return new TestEventCollector();
    }

    @Bean
    public FilterRegistrationBean<Filter> tenantContextTestFilter() {
        return new FilterRegistrationBean<>(new jakarta.servlet.Filter() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response,
                                 jakarta.servlet.FilterChain chain) throws java.io.IOException,
                    jakarta.servlet.ServletException {
                jakarta.servlet.http.HttpServletRequest httpRequest = (jakarta.servlet.http.HttpServletRequest) request;
                String tenantId = httpRequest.getHeader("X-Tenant-Id");
                try {
                    if (tenantId != null && !tenantId.isBlank()) {
                        cn.slainc.core.context.TenantContext.set(Long.parseLong(tenantId));
                    }
                    chain.doFilter(request, response);
                } finally {
                    cn.slainc.core.context.TenantContext.clear();
                }
            }
        });
    }

    @Bean
    public AuthDemoController authDemoController(SecurityLoginService loginService) {
        return new AuthDemoController(loginService);
    }

    @Bean
    public ProtectedDemoController protectedDemoController(SecurityLoginService loginService) {
        return new ProtectedDemoController(loginService);
    }

    @Bean
    public SessionAdminController sessionAdminController(
            cn.slainc.session.online.OnlineUserService onlineUserService,
            cn.slainc.session.query.SessionQueryService queryService,
            cn.slainc.session.kick.KickSessionService kickSessionService,
            cn.slainc.session.stats.SessionStatsService statsService,
            cn.slainc.session.extend.SessionExtender extender,
            cn.slainc.session.extend.SessionRenewalService renewalService,
            org.springframework.beans.factory.ObjectProvider<cn.slainc.session.cleanup.SessionCleanupService> cleanup) {
        return new SessionAdminController(onlineUserService, queryService, kickSessionService, statsService, extender,
                renewalService, cleanup);
    }

    @org.springframework.web.bind.annotation.RestControllerAdvice
    public static class DemoExceptionAdvice {

        @org.springframework.web.bind.annotation.ExceptionHandler(cn.dev33.satoken.exception.NotLoginException.class)
        public cn.slainc.core.result.Result<Void> handleNotLogin(cn.dev33.satoken.exception.NotLoginException e) {
            return cn.slainc.core.result.Result.fail(401000, "注解校验未通过：" + e.getMessage());
        }

        @org.springframework.web.bind.annotation.ExceptionHandler(cn.slainc.core.exception.BizException.class)
        public cn.slainc.core.result.Result<Void> handleBiz(cn.slainc.core.exception.BizException e) {
            return cn.slainc.core.result.Result.fail(400100, e.getMessage());
        }

        @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
        public cn.slainc.core.result.Result<Void> handleIllegal(IllegalArgumentException e) {
            return cn.slainc.core.result.Result.fail(400100, e.getMessage());
        }
    }
}
