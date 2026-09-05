package cn.slainc.sensitive.integration;

import cn.slainc.sensitive.core.SensitiveContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@SpringBootConfiguration
@EnableAutoConfiguration
public class SensitiveTestApplication {

    @Bean
    public SimpleMeterRegistry simpleMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    public FilterRegistrationBean<SensitiveHeaderFilter> sensitiveHeaderFilter() {
        FilterRegistrationBean<SensitiveHeaderFilter> registration =
                new FilterRegistrationBean<>(new SensitiveHeaderFilter());
        registration.addUrlPatterns("/*");
        registration.setName("sensitiveHeaderFilter");
        return registration;
    }

    // 上下文必须在 doFilter 内设置：控制器 return 之后 finally 就执行了，而 JSON 序列化发生在响应写出阶段
    static class SensitiveHeaderFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (Boolean.parseBoolean(request.getHeader("X-Sensitive-Bypass"))) {
                SensitiveContext.disable();
                try {
                    chain.doFilter(request, response);
                } finally {
                    SensitiveContext.clear();
                }
                return;
            }
            String group = request.getHeader("X-Sensitive-Group");
            if (group == null || group.isEmpty()) {
                chain.doFilter(request, response);
                return;
            }
            SensitiveContext.setGroups(group.split(","));
            try {
                chain.doFilter(request, response);
            } finally {
                SensitiveContext.clear();
            }
        }
    }
}
