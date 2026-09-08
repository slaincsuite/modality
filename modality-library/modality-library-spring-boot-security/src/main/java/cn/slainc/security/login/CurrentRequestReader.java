package cn.slainc.security.login;

import cn.slainc.utils.net.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class CurrentRequestReader {

    public HttpServletRequest currentRequest() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                return attributes.getRequest();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    public String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        try {
            return IpUtils.resolveClientIp(request.getHeader("X-Forwarded-For"), request.getHeader("X-Real-IP"),
                    request.getHeader("CF-Connecting-IP"), request.getRemoteAddr());
        } catch (Exception e) {
            return null;
        }
    }

    public String userAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader("User-Agent");
    }

    public String header(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader(name);
    }
}
