package cn.slainc.services.livemonitor.service.stream;

import cn.slainc.services.livemonitor.LiveMonitorKeys;
import cn.slainc.services.livemonitor.support.StreamUrlGuard;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

public class LiveStreamProbe {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;

    private final StreamUrlGuard streamUrlGuard;

    private final Duration timeout;

    public LiveStreamProbe(StreamUrlGuard streamUrlGuard) {
        this(defaultClient(DEFAULT_TIMEOUT), streamUrlGuard, DEFAULT_TIMEOUT);
    }

    LiveStreamProbe(HttpClient httpClient, StreamUrlGuard streamUrlGuard, Duration timeout) {
        this.httpClient = httpClient;
        this.streamUrlGuard = streamUrlGuard;
        this.timeout = timeout;
    }

    public StreamProbeResult probe(String url) {
        URI target = streamUrlGuard.validate(url);
        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(timeout)
                .header("User-Agent", "Modality-Live-Monitor/1.0")
                .GET()
                .build();
        long start = System.nanoTime();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            closeQuietly(response.body());
            long latency = elapsedMillis(start);
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 400) {
                return new StreamProbeResult(LiveMonitorKeys.STATUS_UP, latency, null);
            }
            return new StreamProbeResult(LiveMonitorKeys.STATUS_DOWN, latency,
                    "HTTP 状态码 " + statusCode + "，直播源不可用");
        } catch (HttpTimeoutException exception) {
            return down(start, "直播源探测超时（" + timeout.toSeconds() + " 秒）");
        } catch (IOException exception) {
            return down(start, "连接直播源失败，请检查地址或网络");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return down(start, "直播源探测被中断");
        }
    }

    private StreamProbeResult down(long start, String error) {
        return new StreamProbeResult(LiveMonitorKeys.STATUS_DOWN, elapsedMillis(start), error);
    }

    private static long elapsedMillis(long start) {
        return Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
    }

    private static void closeQuietly(InputStream body) {
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (IOException ignored) {
            // 已拿到响应头即视为探测完成，关闭流失败不影响可用性判定
        }
    }

    private static HttpClient defaultClient(Duration timeout) {
        return HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
