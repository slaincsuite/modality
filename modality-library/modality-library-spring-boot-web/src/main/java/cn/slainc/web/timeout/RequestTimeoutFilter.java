package cn.slainc.web.timeout;

import cn.slainc.core.context.RequestContext;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RequestTimeoutFilter extends OncePerRequestFilter {

    public static final String TIMEOUT_ATTRIBUTE = "cn.slainc.web.timeout.timedOut";

    private final RequestTimeoutWatch watch;

    public RequestTimeoutFilter(RequestTimeoutWatch watch) {
        this.watch = watch;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!watch.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String id = watch.begin(request.getMethod(), request.getRequestURI(), RequestContext.clientIp());
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (request.isAsyncStarted()) {
                watchAsyncCompletion(request, response, id);
            } else {
                watch.end(id, response.getStatus());
            }
        }
    }

    private void watchAsyncCompletion(HttpServletRequest request, HttpServletResponse response, String id) {
        try {
            AsyncContext asyncContext = request.getAsyncContext();
            asyncContext.addListener(new AsyncListener() {

                @Override
                public void onComplete(AsyncEvent event) {
                    watch.end(id, status(event));
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                    request.setAttribute(TIMEOUT_ATTRIBUTE, Boolean.TRUE);
                    watch.end(id, status(event));
                }

                @Override
                public void onError(AsyncEvent event) {
                    watch.end(id, status(event));
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                }
            }, request, response);
        } catch (IllegalStateException exception) {
            watch.end(id, response.getStatus());
        }
    }

    private int status(AsyncEvent event) {
        if (event.getSuppliedResponse() instanceof HttpServletResponse response) {
            return response.getStatus();
        }
        return 0;
    }
}
