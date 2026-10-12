package cn.slainc.monitor.alert;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

public class HttpWebhookTransport implements WebhookTransport {

    private final RestClient restClient;

    private final boolean allowInternalAddresses;

    public HttpWebhookTransport(Duration connectTimeout, Duration readTimeout) {
        this(connectTimeout, readTimeout, false);
    }

    public HttpWebhookTransport(Duration connectTimeout, Duration readTimeout, boolean allowInternalAddresses) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout == null ? Duration.ofSeconds(5) : readTimeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.allowInternalAddresses = allowInternalAddresses;
    }

    @Override
    public String post(String url, String body, Map<String, String> headers) {
        WebhookUrlValidator.validate(url, allowInternalAddresses);
        RestClient.RequestBodySpec spec = restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON);
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && value != null) {
                    spec.header(key, value);
                }
            });
        }
        return spec.body(body).retrieve().body(String.class);
    }
}
