package cn.slainc.session;

import cn.slainc.utils.json.JsonUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record SessionKickNotice(String loginType, String loginId, String tokenValue, String deviceType,
                                String reason, LocalDateTime kickedAt) {

    public String message() {
        return "您的会话已被下线，原因：" + (reason == null || reason.isBlank() ? "管理员强制下线" : reason);
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "session-kicked");
        payload.put("loginType", loginType);
        payload.put("loginId", loginId);
        payload.put("tokenValue", tokenValue);
        payload.put("deviceType", deviceType);
        payload.put("reason", reason);
        payload.put("kickedAt", kickedAt == null ? null : kickedAt.toString());
        payload.put("message", message());
        return payload;
    }

    public String toJson() {
        return JsonUtils.toJson(toPayload());
    }
}
