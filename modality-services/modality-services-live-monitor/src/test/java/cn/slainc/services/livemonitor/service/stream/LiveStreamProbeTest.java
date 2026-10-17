package cn.slainc.services.livemonitor.service.stream;

import cn.slainc.core.exception.BizException;
import cn.slainc.services.livemonitor.LiveMonitorKeys;
import cn.slainc.services.livemonitor.support.StreamUrlGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveStreamProbeTest {

    private final HttpClient httpClient = mock(HttpClient.class);

    private final HttpResponse<InputStream> response = mock(HttpResponse.class);

    private final StreamUrlGuard guard = new StreamUrlGuard();

    private LiveStreamProbe probe;

    @BeforeEach
    void setUp() {
        probe = new LiveStreamProbe(httpClient, guard, Duration.ofSeconds(5));
        when(response.body()).thenReturn(InputStream.nullInputStream());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldProbeWithJdkClientGetAndFiveSecondTimeout() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        StreamProbeResult result = probe.probe("https://8.8.8.8/live.m3u8");

        assertThat(result.status()).isEqualTo(LiveMonitorKeys.STATUS_UP);
        assertThat(result.error()).isNull();
        assertThat(result.latencyMs()).isNotNegative();
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any());
        assertThat(request.getValue().method()).isEqualTo("GET");
        assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(5));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldRecordHttpStatusAndIoFailure() throws Exception {
        when(response.statusCode()).thenReturn(503);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        StreamProbeResult failed = probe.probe("https://8.8.8.8/failed.m3u8");
        assertThat(failed.status()).isEqualTo(LiveMonitorKeys.STATUS_DOWN);
        assertThat(failed.error()).contains("503").contains("不可用");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("连接被拒绝"));
        StreamProbeResult unreachable = probe.probe("https://1.1.1.1/unreachable.m3u8");
        assertThat(unreachable.status()).isEqualTo(LiveMonitorKeys.STATUS_DOWN);
        assertThat(unreachable.error()).isEqualTo("连接直播源失败，请检查地址或网络");
    }

    @Test
    void shouldRejectInternalTargetBeforeHttpCall() {
        assertThatThrownBy(() -> probe.probe("http://127.0.0.1/live"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("内网");
        verifyNoInteractions(httpClient);
    }
}
