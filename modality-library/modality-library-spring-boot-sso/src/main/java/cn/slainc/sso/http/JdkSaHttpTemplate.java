package cn.slainc.sso.http;

import cn.dev33.satoken.http.SaHttpTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class JdkSaHttpTemplate implements SaHttpTemplate {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public String get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("SSO HTTP 请求失败：" + url, e);
        }
    }

    @Override
    public String postByFormData(String url, Map<String, Object> params) {
        try {
            StringBuilder form = new StringBuilder();
            if (params != null) {
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    if (form.length() > 0) {
                        form.append('&');
                    }
                    form.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                    form.append('=');
                    form.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
                }
            }
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("SSO HTTP 请求失败：" + url, e);
        }
    }
}
