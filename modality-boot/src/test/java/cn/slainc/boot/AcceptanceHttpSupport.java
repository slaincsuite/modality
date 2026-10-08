package cn.slainc.boot;

import com.jayway.jsonpath.JsonPath;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// HTTP 样板：TENANT_USER 头 + satoken 透传 + 登录取 token，超时默认 15s（批次 12 文件链路用 60s 实例）
final class AcceptanceHttpSupport {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final int port;

    private final Duration timeout;

    AcceptanceHttpSupport(int port) {
        this(port, Duration.ofSeconds(15));
    }

    AcceptanceHttpSupport(int port, Duration timeout) {
        this.port = port;
        this.timeout = timeout;
    }

    HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-Login-Type", "TENANT_USER");
        if (token != null) {
            builder.header("satoken", token);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<byte[]> sendBytes(String method, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(timeout)
                .header("X-Login-Type", "TENANT_USER");
        if (token != null) {
            builder.header("satoken", token);
        }
        builder.method(method, HttpRequest.BodyPublishers.noBody());
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    String login(String username, String password, String deviceId) throws Exception {
        HttpResponse<String> response = send("POST", "/auth/login", null, """
                {"username":"%s","password":"%s","deviceType":"PC","deviceId":"%s"}
                """.formatted(username, password, deviceId));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.parse(response.body()).read("$.data.tokenValue");
    }
}
