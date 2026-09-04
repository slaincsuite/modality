package cn.slainc.monitor.alert;

import java.util.Map;

public interface WebhookTransport {

    String post(String url, String body, Map<String, String> headers);
}
