package cn.slainc.web.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RequestTimeoutWatch implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RequestTimeoutWatch.class);

    private final RequestTimeoutProperties properties;

    private final Clock clock;

    private final Map<String, InFlight> inFlight = new ConcurrentHashMap<>();

    private final AtomicLong overdueTotal = new AtomicLong();

    private final ScheduledExecutorService scheduler;

    public RequestTimeoutWatch(RequestTimeoutProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public RequestTimeoutWatch(RequestTimeoutProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        if (isEnabled()) {
            long interval = Math.max(thresholdMillis() / 2, 1000L);
            this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable ->
                    Thread.ofPlatform().daemon(true).name("modality-request-timeout-", 0).unstarted(runnable));
            this.scheduler.scheduleWithFixedDelay(this::sweepQuietly, interval, interval, TimeUnit.MILLISECONDS);
        } else {
            this.scheduler = null;
        }
    }

    public boolean isEnabled() {
        return properties.isEnabled() && properties.isObserve();
    }

    public String begin(String method, String uri, String clientIp) {
        if (!isEnabled()) {
            return null;
        }
        String id = UUID.randomUUID().toString();
        inFlight.put(id, new InFlight(method, uri, clientIp, clock.millis(), new AtomicBoolean()));
        return id;
    }

    public void end(String id, int status) {
        if (id == null) {
            return;
        }
        InFlight entry = inFlight.remove(id);
        if (entry == null) {
            return;
        }
        long elapsed = clock.millis() - entry.startMillis();
        if (elapsed > thresholdMillis() && entry.counted().compareAndSet(false, true)) {
            overdueTotal.incrementAndGet();
            log.warn("请求处理超时（仅观测，同步请求无法中断）：{} {} 耗时 {} ms，阈值 {} ms，响应状态 {}，客户端 {}",
                    entry.method(), entry.uri(), elapsed, thresholdMillis(), status, entry.clientIp());
        }
    }

    public int sweep() {
        if (!isEnabled()) {
            return 0;
        }
        long now = clock.millis();
        int overdue = 0;
        for (InFlight entry : inFlight.values()) {
            long elapsed = now - entry.startMillis();
            if (elapsed > thresholdMillis() && entry.counted().compareAndSet(false, true)) {
                overdue++;
                overdueTotal.incrementAndGet();
                log.warn("请求处理超过观测阈值（仍在执行，同步请求无法中断）：{} {} 已耗时 {} ms，阈值 {} ms，客户端 {}",
                        entry.method(), entry.uri(), elapsed, thresholdMillis(), entry.clientIp());
            }
        }
        return overdue;
    }

    public List<String> overdueRequests() {
        long now = clock.millis();
        List<String> overdue = new ArrayList<>();
        for (InFlight entry : inFlight.values()) {
            if (now - entry.startMillis() > thresholdMillis()) {
                overdue.add(entry.method() + " " + entry.uri());
            }
        }
        return overdue;
    }

    public int inFlightCount() {
        return inFlight.size();
    }

    public long overdueTotal() {
        return overdueTotal.get();
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void sweepQuietly() {
        try {
            sweep();
        } catch (RuntimeException exception) {
            log.warn("请求超时巡检异常：{}", exception.getMessage());
        }
    }

    private long thresholdMillis() {
        return properties.getObserveTimeout() == null ? 30_000L : properties.getObserveTimeout().toMillis();
    }

    private record InFlight(String method, String uri, String clientIp, long startMillis, AtomicBoolean counted) {
    }
}
