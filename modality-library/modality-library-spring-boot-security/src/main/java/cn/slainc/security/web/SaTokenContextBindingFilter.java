package cn.slainc.security.web;

import cn.dev33.satoken.servlet.util.SaTokenContextJakartaServletUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SaTokenContextBindingFilter implements Filter {

    private final Filter delegate;

    public SaTokenContextBindingFilter(Filter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            SaTokenContextJakartaServletUtil.setContext((HttpServletRequest) request,
                    (HttpServletResponse) response);
            delegate.doFilter(request, response, chain);
        } finally {
            SaTokenContextJakartaServletUtil.clearContext();
        }
    }
}
