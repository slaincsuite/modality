package cn.slainc.web.websocket;

import cn.slainc.utils.json.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record WebSocketCommand(String type, String topic, String payload) {

    public static final String PING = "ping";

    public static final String PONG = "pong";

    public static final String SUBSCRIBE = "subscribe";

    public static final String UNSUBSCRIBE = "unsubscribe";

    public static final String MESSAGE = "message";

    public static WebSocketCommand parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        if (!text.startsWith("{")) {
            return new WebSocketCommand(text.toLowerCase(Locale.ROOT), null, text);
        }
        try {
            Map<String, Object> values = JsonUtils.parseMap(text);
            String type = text(values.get("type"));
            return new WebSocketCommand(type == null ? MESSAGE : type.toLowerCase(Locale.ROOT), text(values.get("topic")),
                    text(values.get("payload")));
        } catch (RuntimeException exception) {
            return new WebSocketCommand(MESSAGE, null, text);
        }
    }

    public static String reply(String type, String topic, String message) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("type", type);
        if (topic != null) {
            values.put("topic", topic);
        }
        if (message != null) {
            values.put("message", message);
        }
        return JsonUtils.toJson(values);
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
