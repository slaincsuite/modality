package cn.slainc.sse.spi;

import cn.slainc.core.exception.BizException;
import cn.slainc.sse.support.SseServiceImpl;
import cn.slainc.sse.handshake.SseAuthenticator;
import cn.slainc.sse.handshake.SseHandshake;
import cn.slainc.sse.handshake.SseUser;
import cn.slainc.sse.autoconfigure.SseProperties;
import cn.slainc.sse.cluster.SseEventRelay;
import cn.slainc.sse.support.SseConnectionRegistry;
import cn.slainc.sse.support.SseEvent;
import cn.slainc.sse.support.SseEventStore;
import cn.slainc.sse.support.SseMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseServiceTest {

    private final SseProperties properties = new SseProperties();

    private final SseEventStore eventStore = new SseEventStore(properties.getReplayBufferSize());

    private final SseConnectionRegistry registry = new SseConnectionRegistry();

    private final RecordingSseService service = new RecordingSseService(registry, eventStore, properties);

    @Test
    void shouldNeverDeliverOtherUsersEvents() {
        service.subscribe(1L);
        service.subscribe(2L);

        assertThat(service.send(2L, "secret", "for-b")).isEqualTo(1);
        assertThat(service.send(1L, "notice", "for-a")).isEqualTo(1);

        assertThat(frames(service.emitter(0))).contains("for-a").doesNotContain("for-b");
        assertThat(frames(service.emitter(1))).contains("for-b").doesNotContain("for-a");
    }

    @Test
    void shouldNotReplayAnotherUsersEventWhenLastEventIdIsForged() {
        service.subscribe(1L);
        service.subscribe(2L);
        service.send(2L, "secret", "for-b");

        String forgedId = eventId(service.emitter(1), 1);
        assertThat(forgedId).isNotBlank();
        assertThat(eventStore.contains("2", forgedId)).isTrue();
        assertThat(eventStore.contains("1", forgedId)).isFalse();

        service.subscribe(1L, forgedId);

        assertThat(frames(service.emitter(2))).doesNotContain("for-b");
    }

    @Test
    void shouldReplayOwnEventsAfterReconnect() {
        service.subscribe(1L);
        service.send(1L, "notice", "first");
        service.send(1L, "notice", "second");

        String firstEventId = eventId(service.emitter(0), 1);
        assertThat(firstEventId).isNotBlank();

        service.subscribe(1L, firstEventId);

        assertThat(frames(service.emitter(1))).contains("second");
    }

    @Test
    void shouldKeepHeartbeatsOutOfTheReplayBuffer() {
        service.subscribe(1L);
        service.send(1L, "notice", "for-a");
        int buffered = eventStore.size("1");

        assertThat(service.heartbeat()).isEqualTo(1);

        assertThat(frames(service.emitter(0))).contains("for-a");
        assertThat(eventStore.size("1")).isEqualTo(buffered);
        assertThat(eventStore.size()).isEqualTo(buffered);
    }

    @Test
    void shouldNotBufferEventsWithoutRecipients() {
        assertThat(service.send(9L, "notice", "nobody")).isZero();
        assertThat(service.send(null, "notice", "nobody")).isZero();
        assertThat(service.heartbeat()).isZero();
        assertThat(eventStore.size()).isZero();
    }

    @Test
    void shouldEnforceMaxConnectionsPerUser() {
        properties.setMaxConnectionsPerUser(1);
        service.subscribe(1L);

        assertThatThrownBy(() -> service.subscribe(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上限");
        assertThat(service.connections(1L)).isEqualTo(1);
        assertThat(service.canConnect(1L)).isFalse();
        assertThat(service.metrics().connectionsRejected()).isEqualTo(1);

        service.disconnect(registry.byUser(1L).get(0).connectionId());
        assertThat(service.canConnect(1L)).isTrue();
        service.subscribe(1L);
        assertThat(service.connections(1L)).isEqualTo(1);
    }

    @Test
    void shouldRejectSubscriptionsWithoutUser() {
        assertThatThrownBy(() -> service.subscribe((Long) null)).isInstanceOf(BizException.class);
        assertThat(registry.count()).isZero();
    }

    @Test
    void shouldRelayEventsAndDeliverRemoteEventsLocally() {
        RecordingRelay relay = new RecordingRelay();
        SseService relaying = new SseServiceImpl(registry, eventStore, properties, new SseMetrics(), relay);

        relaying.send(5L, "notice", "remote-only");
        assertThat(relay.published).hasSize(1);
        assertThat(relay.published.get(0).userId()).isEqualTo(5L);
        assertThat(eventStore.size("5")).isZero();

        relaying.subscribe(5L);
        SseEvent remote = new SseEvent("remote-1", "notice", "from-other-node", LocalDateTime.now());
        assertThat(relaying.deliver(5L, remote)).isEqualTo(1);
        assertThat(eventStore.contains("5", "remote-1")).isTrue();
    }

    @Test
    void shouldBroadcastToEveryConnectionAndBufferPerUser() {
        service.subscribe(1L);
        service.subscribe(2L);

        assertThat(service.broadcast("notice", "for-everyone")).isEqualTo(2);
        assertThat(frames(service.emitter(0))).contains("for-everyone");
        assertThat(frames(service.emitter(1))).contains("for-everyone");
        assertThat(eventStore.size("1")).isEqualTo(1);
        assertThat(eventStore.size("2")).isEqualTo(1);
        assertThat(service.connections()).isEqualTo(2);
    }

    @Test
    void shouldAuthenticateHandshakeBeforeSubscribing() {
        RecordingSseService authenticating = new RecordingSseService(registry, eventStore, properties,
                handshake -> new SseUser(7L, "u7"));

        SseEmitter emitter = authenticating.subscribe(handshake("token=whatever"));

        assertThat(emitter).isNotNull();
        assertThat(authenticating.connections(7L)).isEqualTo(1);
        assertThat(frames(authenticating.emitter(0))).contains("connect");
    }

    @Test
    void shouldRejectHandshakeWhenAuthenticationFails() {
        RecordingSseService rejecting = new RecordingSseService(registry, eventStore, properties,
                handshake -> null);

        assertThatThrownBy(() -> rejecting.subscribe(handshake(null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已认证");
        assertThatThrownBy(() -> rejecting.subscribe(handshake("token=1"), "e-1"))
                .isInstanceOf(BizException.class);
        assertThat(registry.count()).isZero();
    }

    @Test
    void shouldRejectHandshakeWithoutAuthenticator() {
        assertThatThrownBy(() -> service.subscribe(handshake("token=1")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("认证器");
    }

    @Test
    void shouldSwallowAuthenticatorFailures() {
        RecordingSseService failing = new RecordingSseService(registry, eventStore, properties,
                handshake -> {
                    throw new IllegalStateException("认证服务不可用");
                });

        assertThatThrownBy(() -> failing.subscribe(handshake("token=1")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已认证");
    }

    @Test
    void shouldReadLastEventIdHeaderOnHandshakeReconnect() {
        service.subscribe(1L);
        service.send(1L, "notice", "first");
        service.send(1L, "notice", "second");

        String firstEventId = eventId(service.emitter(0), 1);
        assertThat(firstEventId).isNotBlank();

        RecordingSseService reconnecting = new RecordingSseService(registry, eventStore, properties,
                handshake -> new SseUser(1L, "u1"));
        reconnecting.subscribe(handshake(null, SseService.LAST_EVENT_ID_HEADER, firstEventId));

        assertThat(frames(reconnecting.emitter(0))).contains("second");
    }

    private SseHandshake handshake(String query) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (query != null) {
            String[] pair = query.split("=", 2);
            request.setParameter(pair[0], pair[1]);
        }
        return new SseHandshake(request);
    }

    private SseHandshake handshake(String query, String headerName, String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (query != null) {
            String[] pair = query.split("=", 2);
            request.setParameter(pair[0], pair[1]);
        }
        request.addHeader(headerName, headerValue);
        return new SseHandshake(request);
    }

    private String frames(RecordingSseEmitter emitter) {
        return String.join("", emitter.frames());
    }

    private String eventId(RecordingSseEmitter emitter, int frameIndex) {
        List<String> frames = emitter.frames();
        if (frames.size() <= frameIndex) {
            return null;
        }
        for (String line : frames.get(frameIndex).split("\n")) {
            if (line.startsWith("id:")) {
                return line.substring("id:".length());
            }
        }
        return null;
    }

    private static class RecordingSseService extends SseServiceImpl {

        private final List<RecordingSseEmitter> emitters = new CopyOnWriteArrayList<>();

        RecordingSseService(SseConnectionRegistry registry, SseEventStore eventStore, SseProperties properties) {
            super(registry, eventStore, properties);
        }

        RecordingSseService(SseConnectionRegistry registry, SseEventStore eventStore, SseProperties properties,
                            SseAuthenticator authenticator) {
            super(registry, eventStore, properties, new SseMetrics(), SseEventRelay.local(), authenticator);
        }

        @Override
        protected SseEmitter createEmitter() {
            RecordingSseEmitter emitter = new RecordingSseEmitter();
            emitters.add(emitter);
            return emitter;
        }

        RecordingSseEmitter emitter(int index) {
            return emitters.get(index);
        }
    }

    private static class RecordingSseEmitter extends SseEmitter {

        private final List<String> frames = new CopyOnWriteArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            StringBuilder frame = new StringBuilder();
            for (DataWithMediaType data : builder.build()) {
                frame.append(data.getData());
            }
            frames.add(frame.toString());
        }

        List<String> frames() {
            return frames;
        }
    }

    private static class RecordingRelay implements SseEventRelay {

        private final List<Published> published = new CopyOnWriteArrayList<>();

        @Override
        public void publish(Long userId, SseEvent event) {
            published.add(new Published(userId, event));
        }

        private record Published(Long userId, SseEvent event) {
        }
    }
}
