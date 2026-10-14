package cn.slainc.sse.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

import java.time.Duration;
import java.time.format.DateTimeParseException;

public class DynamicSseProperties extends SseProperties {

    public static final String KEY_TIMEOUT = "modality.sse.timeout";

    public static final String KEY_MAX_CONNECTIONS_PER_USER = "modality.sse.max-connections-per-user";

    public static final String KEY_HEARTBEAT_EVENT = "modality.sse.heartbeat-event";

    public static final String KEY_RECONNECT_TIME = "modality.sse.reconnect-time";

    private static final Logger log = LoggerFactory.getLogger(DynamicSseProperties.class);

    private final SseProperties fallback;

    private final DynamicConfigService service;

    public DynamicSseProperties(SseProperties fallback, DynamicConfigService service) {
        this.fallback = fallback;
        this.service = service;
        BeanUtils.copyProperties(fallback, this);
    }

    @Override
    public Duration getTimeout() {
        return service == null ? fallback.getTimeout() : duration(KEY_TIMEOUT, fallback.getTimeout());
    }

    @Override
    public int getMaxConnectionsPerUser() {
        if (service == null) {
            return fallback.getMaxConnectionsPerUser();
        }
        try {
            return service.getInt(KEY_MAX_CONNECTIONS_PER_USER, fallback.getMaxConnectionsPerUser());
        } catch (RuntimeException exception) {
            log.warn("读取动态配置 {} 失败，已回退为 {}，原因={}", KEY_MAX_CONNECTIONS_PER_USER,
                    fallback.getMaxConnectionsPerUser(), exception.getMessage());
            return fallback.getMaxConnectionsPerUser();
        }
    }

    @Override
    public String getHeartbeatEvent() {
        if (service == null) {
            return fallback.getHeartbeatEvent();
        }
        try {
            return service.getString(KEY_HEARTBEAT_EVENT, fallback.getHeartbeatEvent());
        } catch (RuntimeException exception) {
            log.warn("读取动态配置 {} 失败，已回退为 {}，原因={}", KEY_HEARTBEAT_EVENT,
                    fallback.getHeartbeatEvent(), exception.getMessage());
            return fallback.getHeartbeatEvent();
        }
    }

    @Override
    public Duration getReconnectTime() {
        return service == null ? fallback.getReconnectTime() : duration(KEY_RECONNECT_TIME, fallback.getReconnectTime());
    }

    private Duration duration(String key, Duration fallbackValue) {
        String value = read(key);
        if (StringUtils.isBlank(value)) {
            return fallbackValue;
        }
        String text = value.trim();
        try {
            return Duration.parse(text);
        } catch (DateTimeParseException ignored) {
            // 非 ISO-8601 时按纯秒数解析，仍失败则回退默认值
        }
        try {
            return Duration.ofSeconds(Long.parseLong(text));
        } catch (RuntimeException exception) {
            log.warn("动态配置 {} 不是合法的时间跨度，已回退为 {}", key, fallbackValue);
            return fallbackValue;
        }
    }

    private String read(String key) {
        try {
            return service.findString(key).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("读取动态配置 {} 失败，已回退本地配置，原因={}", key, exception.getMessage());
            return null;
        }
    }
}
