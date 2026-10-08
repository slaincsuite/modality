package cn.slainc.tenant.context;

import cn.slainc.core.context.TenantContext;
import cn.slainc.tenant.resolver.TenantResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantConsistencyFilterTest {

    private final HttpServletRequest request = mock(HttpServletRequest.class);

    private final HttpServletResponse response = mock(HttpServletResponse.class);

    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("客户端声明的租户与会话租户一致时放行并以会话租户覆盖上下文")
    void consistentClaimPasses() throws Exception {
        TenantResolver session = resolver(9L);
        TenantResolver claim = resolver(9L);
        TenantConsistencyFilter filter = new TenantConsistencyFilter(session, List.of(claim));

        filter.doFilter(request, response, chain);

        assertThat(TenantContext.get()).isEqualTo(9L);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("客户端声明的租户与会话租户不一致时拒绝并返回 403")
    void inconsistentClaimRejected() throws Exception {
        TenantResolver session = resolver(9L);
        TenantResolver claim = resolver(2L);
        TenantConsistencyFilter filter = new TenantConsistencyFilter(session, List.of(claim));
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(403);
        assertThat(body.toString()).contains("请求租户与登录会话租户不一致");
        verify(chain, never()).doFilter(any(), any());
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    @DisplayName("无会话租户时不干预，客户端自报租户留给租户过滤器处理")
    void anonymousRequestUntouched() throws Exception {
        TenantConsistencyFilter filter = new TenantConsistencyFilter(resolver(null), List.of(resolver(2L)));

        filter.doFilter(request, response, chain);

        assertThat(TenantContext.get()).isNull();
        verify(chain).doFilter(request, response);
    }

    private static TenantResolver resolver(Long tenantId) {
        return new TenantResolver() {
            @Override
            public Long resolve(HttpServletRequest request) {
                return tenantId;
            }

            @Override
            public int order() {
                return 0;
            }
        };
    }
}
