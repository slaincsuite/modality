package cn.slainc.monitor.alert;

import cn.slainc.crypto.hmac.HmacAlgorithm;
import cn.slainc.crypto.hmac.HmacUtils;
import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public final class WebhookSignatures {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatures.class);

    private WebhookSignatures() {
    }

    public static String dingTalkSign(long timestamp, String secret) {
        byte[] mac = HmacUtils.hmac(HmacAlgorithm.HMAC_SHA256, secret.getBytes(StandardCharsets.UTF_8),
                timestamp + "\n" + secret);
        return Base64.getEncoder().encodeToString(mac);
    }

    public static String dingTalkSignedUrl(String url, String secret, long timestamp) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "timestamp=" + timestamp + "&sign="
                + URLEncoder.encode(dingTalkSign(timestamp, secret), StandardCharsets.UTF_8);
    }

    public static void verifyPlatformResponse(String response) {
        if (response == null || response.isBlank()) {
            return;
        }
        try {
            Map<String, Object> body = JsonUtils.parseMap(response);
            Object code = body.get("errcode") == null ? body.get("code") : body.get("errcode");
            if (code instanceof Number number && number.intValue() != 0) {
                throw new IllegalStateException("Webhook 返回错误码 " + number + "，响应内容: " + response);
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.debug("Webhook 响应不是合法 JSON，跳过错误码校验: {}", exception.getMessage());
        }
    }
}
