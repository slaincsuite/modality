package cn.slainc.web.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class WebSocketHandshake {

    private final ServerHttpRequest request;

    private final Map<String, Object> attributes;

    public WebSocketHandshake(ServerHttpRequest request, Map<String, Object> attributes) {
        this.request = request;
        this.attributes = attributes;
    }

    public String header(String name) {
        List<String> values = request.getHeaders().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    public String parameter(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst(name);
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public URI uri() {
        return request.getURI();
    }
}
