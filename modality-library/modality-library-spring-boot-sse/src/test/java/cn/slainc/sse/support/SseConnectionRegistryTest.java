package cn.slainc.sse.support;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SseConnectionRegistryTest {

    private final SseConnectionRegistry registry = new SseConnectionRegistry();

    @Test
    void shouldRegisterAndCountConnectionsPerUser() {
        assertThat(registry.register("c-1", 1L, new SseEmitter(), 0)).isTrue();
        assertThat(registry.register("c-2", 1L, new SseEmitter(), 0)).isTrue();
        assertThat(registry.register("c-3", 2L, new SseEmitter(), 0)).isTrue();

        assertThat(registry.count()).isEqualTo(3);
        assertThat(registry.countByUser(1L)).isEqualTo(2);
        assertThat(registry.byUser(1L)).extracting(SseConnectionRegistry.Connection::connectionId)
                .containsExactlyInAnyOrder("c-1", "c-2");
        assertThat(registry.byUser(2L)).hasSize(1);
        assertThat(registry.countByUser(null)).isZero();
        assertThat(registry.byUser(null)).isEmpty();

        registry.unregister("c-1");
        assertThat(registry.count()).isEqualTo(2);
        assertThat(registry.countByUser(1L)).isEqualTo(1);
        registry.unregister("c-1");
        assertThat(registry.countByUser(1L)).isEqualTo(1);
        registry.unregister("c-2");
        assertThat(registry.countByUser(1L)).isZero();
        assertThat(registry.all()).hasSize(1);
    }

    @Test
    void shouldEnforcePerUserLimit() {
        assertThat(registry.register("c-1", 1L, new SseEmitter(), 1)).isTrue();
        assertThat(registry.register("c-2", 1L, new SseEmitter(), 1)).isFalse();
        assertThat(registry.register("c-3", 2L, new SseEmitter(), 1)).isTrue();
        assertThat(registry.count()).isEqualTo(2);
        assertThat(registry.countByUser(1L)).isEqualTo(1);
    }

    @Test
    void shouldSendEventsAndRemoveConnection() {
        SseEmitter emitter = new SseEmitter();
        assertThat(registry.register("c-1", 1L, emitter, 0)).isTrue();
        SseConnectionRegistry.Connection connection = registry.byUser(1L).get(0);

        assertThat(registry.send(connection, "notice", "hello", "e-1")).isTrue();
        assertThat(registry.send(connection, "notice", "hello", null)).isTrue();
        assertThat(registry.send(connection, "notice", "hello", "e-2", 5000L)).isTrue();
        assertThat(registry.send(null, "notice", "hello", null)).isFalse();
        assertThat(registry.send(connection, null)).isFalse();

        registry.remove("c-1");
        assertThat(registry.count()).isZero();
    }

    @Test
    void shouldDropConnectionWhenSendFails() {
        assertThat(registry.register("c-1", 1L, new FailingSseEmitter(), 0)).isTrue();
        assertThat(registry.register("c-2", 1L, new SseEmitter(), 0)).isTrue();

        SseConnectionRegistry.Connection failing = registry.byUser(1L).stream()
                .filter(connection -> "c-1".equals(connection.connectionId()))
                .findFirst()
                .orElseThrow();
        assertThat(registry.send(failing, "notice", "hello", "e-1")).isFalse();
        assertThat(registry.count()).isEqualTo(1);
        assertThat(registry.countByUser(1L)).isEqualTo(1);
    }

    @Test
    void shouldIgnoreInvalidRegistration() {
        assertThat(registry.register(null, 1L, new SseEmitter(), 0)).isFalse();
        assertThat(registry.register("c-1", 1L, null, 0)).isFalse();
        assertThat(registry.count()).isZero();
        registry.remove(null);
        assertThat(registry.count()).isZero();
    }

    @Test
    void shouldRegisterEmitterCallbacks() {
        SseEmitter emitter = mock(SseEmitter.class);
        assertThat(registry.register("c-1", 1L, emitter, 0)).isTrue();
        verify(emitter).onCompletion(any(Runnable.class));
        verify(emitter).onTimeout(any(Runnable.class));
        verify(emitter).onError(any());
        registry.remove("c-1");
        verify(emitter, times(1)).complete();
    }

    private static class FailingSseEmitter extends SseEmitter {

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("connection closed");
        }
    }
}
