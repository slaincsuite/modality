package cn.slainc.modules.auth.login;

import cn.slainc.security.LoginTypes;
import cn.slainc.security.StpKit;
import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.session.extend.SessionExtender;
import cn.slainc.session.extend.SessionRenewalService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 滑动续期：剩余有效期低于阈值时按配置续期，记住我令牌按记住我时长续，其余按会话配置续
public class SessionRenewalFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionRenewalFilter.class);

    private final AuthProperties properties;

    private final SessionRenewalService renewalService;

    private final SessionExtender sessionExtender;

    public SessionRenewalFilter(AuthProperties properties, SessionRenewalService renewalService,
                                SessionExtender sessionExtender) {
        this.properties = properties;
        this.renewalService = renewalService;
        this.sessionExtender = sessionExtender;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (properties.getRenewal().isEnabled()) {
            try {
                renewIfNeeded(request);
            } catch (Exception e) {
                log.debug("会话续期检查失败：{}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    private void renewIfNeeded(HttpServletRequest request) {
        var type = LoginTypes.resolveOrDefault(request.getHeader(CurrentLogin.TYPE_HEADER),
                cn.slainc.core.enums.LoginType.TENANT_USER.value());
        var logic = StpKit.of(type);
        if (!logic.isLogin()) {
            return;
        }
        String tokenValue = logic.getTokenValue();
        boolean remember = Boolean.TRUE.equals(sessionExtender.getExtra(type, tokenValue, "remember"));
        long ttl = remember ? properties.getRememberMe().getTtlSeconds() : renewalService.effectiveTtlSeconds(type);
        renewalService.renewIfNeeded(type, tokenValue, properties.getRenewal().getThresholdSeconds(), ttl);
    }
}
