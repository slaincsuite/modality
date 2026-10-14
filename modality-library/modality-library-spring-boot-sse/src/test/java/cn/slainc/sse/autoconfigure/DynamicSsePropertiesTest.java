package cn.slainc.sse.autoconfigure;

import cn.slainc.sse.MapSseConfigService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicSsePropertiesTest {

    private final SseProperties bound = new SseProperties();

    @Test
    void shouldApplyTheStoredConnectionSettings() {
        SseProperties properties = dynamic(Map.of(
                DynamicSseProperties.KEY_TIMEOUT, "PT15M",
                DynamicSseProperties.KEY_MAX_CONNECTIONS_PER_USER, "2",
                DynamicSseProperties.KEY_HEARTBEAT_EVENT, "ping",
                DynamicSseProperties.KEY_RECONNECT_TIME, "PT10S"));

        assertThat(properties.getTimeout()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.getMaxConnectionsPerUser()).isEqualTo(2);
        assertThat(properties.getHeartbeatEvent()).isEqualTo("ping");
        assertThat(properties.getReconnectTime()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldKeepTheBoundSettingsWhenNothingIsStored() {
        SseProperties properties = dynamic(Map.of());

        assertThat(properties.getTimeout()).isEqualTo(bound.getTimeout());
        assertThat(properties.getMaxConnectionsPerUser()).isEqualTo(bound.getMaxConnectionsPerUser());
        assertThat(properties.getHeartbeatEvent()).isEqualTo(bound.getHeartbeatEvent());
        assertThat(properties.getReconnectTime()).isEqualTo(bound.getReconnectTime());
    }

    @Test
    void shouldCarryOverEverySettingTheDynamicViewDoesNotResolve() {
        bound.setReplayBufferSize(17);
        bound.setHeartbeatInterval(Duration.ofSeconds(45));

        SseProperties properties = dynamic(Map.of());

        assertThat(properties.getReplayBufferSize()).isEqualTo(17);
        assertThat(properties.getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void shouldStayBoundWhenNoServiceIsRegistered() {
        SseProperties properties = new DynamicSseProperties(bound, null);

        assertThat(properties.getTimeout()).isEqualTo(bound.getTimeout());
        assertThat(properties.getMaxConnectionsPerUser()).isEqualTo(bound.getMaxConnectionsPerUser());
    }

    @Test
    void shouldFallBackOnIllegalValues() {
        SseProperties properties = dynamic(Map.of(
                DynamicSseProperties.KEY_TIMEOUT, "not-a-duration",
                DynamicSseProperties.KEY_RECONNECT_TIME, "20",
                DynamicSseProperties.KEY_MAX_CONNECTIONS_PER_USER, "not-a-number"));

        assertThat(properties.getTimeout()).isEqualTo(bound.getTimeout());
        assertThat(properties.getReconnectTime()).isEqualTo(Duration.ofSeconds(20));
        assertThat(properties.getMaxConnectionsPerUser()).isEqualTo(bound.getMaxConnectionsPerUser());
    }

    private DynamicSseProperties dynamic(Map<String, String> values) {
        MapSseConfigService service = new MapSseConfigService();
        values.forEach(service::put);
        return new DynamicSseProperties(bound, service);
    }
}
