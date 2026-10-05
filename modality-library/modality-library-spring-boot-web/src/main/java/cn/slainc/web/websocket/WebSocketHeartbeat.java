package cn.slainc.web.websocket;

import cn.slainc.utils.thread.ThreadPoolUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WebSocketHeartbeat implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHeartbeat.class);

    private static final CloseStatus TIMEOUT_STATUS = new CloseStatus(1001, "心跳超时");

    private final WebSocketSessionRegistry registry;

    private final WebSocketProperties properties;

    private final Clock clock;

    private final ScheduledExecutorService scheduler =
            ThreadPoolUtils.newDaemonSingleThreadScheduler("modality-ws-heartbeat-0");

    private volatile boolean running;

    public WebSocketHeartbeat(WebSocketSessionRegistry registry, WebSocketProperties properties) {
        this(registry, properties, Clock.systemUTC());
    }

    public WebSocketHeartbeat(WebSocketSessionRegistry registry, WebSocketProperties properties, Clock clock) {
        this.registry = registry;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void start() {
        long interval = Math.max(properties.getHeartbeatInterval().toMillis(), 100L);
        scheduler.scheduleWithFixedDelay(this::sweepQuietly, interval, interval, TimeUnit.MILLISECONDS);
        running = true;
        log.info("WebSocket 心跳探测已启动：间隔 {} ms，超时 {} ms", interval,
                properties.getHeartbeatTimeout().toMillis());
    }

    @Override
    public void stop() {
        running = false;
        scheduler.shutdownNow();
        log.info("WebSocket 心跳探测已停止");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public int sweep() {
        long now = clock.millis();
        long timeout = properties.getHeartbeatTimeout().toMillis();
        int closed = 0;
        for (WebSocketSession session : registry.sessions()) {
            String sessionId = session.getId();
            long idle = now - registry.lastActiveAt(sessionId);
            if (idle > timeout) {
                log.warn("WebSocket 心跳超时，主动断开连接 sessionId={} 空闲 {} ms 超过 {} ms", sessionId, idle, timeout);
                close(session);
                registry.remove(sessionId);
                closed++;
                continue;
            }
            if (session.isOpen()) {
                try {
                    session.sendMessage(new PingMessage());
                } catch (IOException | IllegalStateException exception) {
                    log.warn("WebSocket 心跳探测失败，移除会话 sessionId={}：{}", sessionId, exception.getMessage());
                    registry.remove(sessionId);
                    closed++;
                }
            }
        }
        return closed;
    }

    private void close(WebSocketSession session) {
        try {
            session.close(TIMEOUT_STATUS);
        } catch (IOException exception) {
            log.warn("WebSocket 会话关闭失败 sessionId={}：{}", session.getId(), exception.getMessage());
        }
    }

    private void sweepQuietly() {
        try {
            sweep();
        } catch (RuntimeException exception) {
            log.warn("WebSocket 心跳巡检异常：{}", exception.getMessage());
        }
    }
}
