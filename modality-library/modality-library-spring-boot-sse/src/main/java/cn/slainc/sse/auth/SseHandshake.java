package cn.slainc.sse.auth;

import jakarta.servlet.http.HttpServletRequest;

public class SseHandshake {

    private final HttpServletRequest request;

    public SseHandshake(HttpServletRequest request) {
        this.request = request;
    }

    public String header(String name) {
        return request.getHeader(name);
    }

    public String parameter(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return request.getParameter(name);
    }

    public String uri() {
        return request.getRequestURI();
    }
}
