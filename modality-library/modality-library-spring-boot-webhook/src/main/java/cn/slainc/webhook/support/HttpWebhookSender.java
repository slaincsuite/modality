package cn.slainc.webhook.support;

import cn.slainc.utils.string.StringUtils;
import cn.slainc.webhook.api.WebhookSender;
import cn.slainc.webhook.config.WebhookProperties;
import cn.slainc.webhook.sender.WebhookSendRequest;
import cn.slainc.webhook.sender.WebhookSendResult;
import cn.slainc.webhook.support.WebhookSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.function.LongSupplier;

public class HttpWebhookSender implements WebhookSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpWebhookSender.class);

    public static final String HEADER_EVENT = "X-Modality-Event";

    public static final String HEADER_DELIVERY = "X-Modality-Delivery";

    private final WebhookProperties properties;

    private final LongSupplier clock;

    public HttpWebhookSender(WebhookProperties properties) {
        this(properties, null);
    }

    public HttpWebhookSender(WebhookProperties properties, LongSupplier epochSecondSupplier) {
        this.properties = properties;
        this.clock = epochSecondSupplier == null ? () -> Instant.now().getEpochSecond() : epochSecondSupplier;
    }

    @Override
    public WebhookSendResult send(WebhookSendRequest request) {
        if (request == null || StringUtils.isBlank(request.url())) {
            return WebhookSendResult.failed("回调地址不能为空");
        }
        long timeoutMillis = request.timeoutMillis() > 0 ? request.timeoutMillis() : properties.getTimeoutMillis();
        RestClient client = client(timeoutMillis);
        try {
            ResponseEntity<String> response = client.post()
                    .uri(request.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.set(HEADER_EVENT, StringUtils.nullToEmpty(request.eventType()));
                        if (request.deliveryId() != null) {
                            headers.set(HEADER_DELIVERY, String.valueOf(request.deliveryId()));
                        }
                        String signature = WebhookSignature.header(request.secret(), clock.getAsLong(),
                                request.body());
                        if (StringUtils.isNotBlank(signature)) {
                            headers.set(properties.getSignatureHeader(), signature);
                        }
                    })
                    .body(StringUtils.nullToEmpty(request.body()))
                    .retrieve()
                    .toEntity(String.class);
            return WebhookSendResult.delivered(response.getStatusCode().value(), response.getBody());
        } catch (RestClientResponseException e) {
            return WebhookSendResult.rejected(e.getStatusCode().value(), e.getResponseBodyAsString(),
                    "HTTP " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            LOGGER.warn("webhook 投递 {} 失败：{}", request.deliveryId(), e.toString());
            return WebhookSendResult.failed(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private RestClient client(long timeoutMillis) {
        int timeout = (int) Math.max(1L, Math.min(timeoutMillis, Integer.MAX_VALUE));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory).build();
    }
}
