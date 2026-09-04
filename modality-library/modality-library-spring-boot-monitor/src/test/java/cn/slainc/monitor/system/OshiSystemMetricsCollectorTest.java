package cn.slainc.monitor.system;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OshiSystemMetricsCollectorTest {

    @Test
    @DisplayName("能采集到 CPU、内存、磁盘、网络、进程与文件描述符")
    void shouldCollectSnapshot() {
        OshiSystemMetricsCollector collector = new OshiSystemMetricsCollector(5000L, List.of(), List.of(), true);
        SystemMetricsSnapshot snapshot = collector.snapshot();
        assertThat(snapshot.getCollectedAt()).isPositive();
        assertThat(snapshot.getCpu().count()).isPositive();
        assertThat(snapshot.getCpu().usage()).isBetween(0d, 1d);
        assertThat(snapshot.getMemory().total()).isPositive();
        assertThat(snapshot.getMemory().used()).isNotNegative();
        assertThat(snapshot.getMemory().usage()).isBetween(0d, 1d);
        assertThat(snapshot.getDisk().mounts()).isNotEmpty();
        assertThat(snapshot.getDisk().mounts().get(0).total()).isPositive();
        assertThat(snapshot.getNetwork().tcpTotal()).isNotNegative();
        assertThat(snapshot.getProcess().processes()).isPositive();
        assertThat(snapshot.getProcess().threads()).isPositive();
        assertThat(snapshot.getUptimeSeconds()).isPositive();
    }

    @Test
    @DisplayName("缓存窗口内重复取值不会再次采集，保证 Gauge 轻量")
    void shouldCacheSnapshot() {
        OshiSystemMetricsCollector collector = new OshiSystemMetricsCollector(60000L, List.of(), List.of(), false);
        SystemMetricsSnapshot first = collector.snapshot();
        SystemMetricsSnapshot second = collector.snapshot();
        assertThat(second).isSameAs(first);
    }

    @Test
    @DisplayName("缓存过期后重新采集")
    void shouldRefreshAfterCacheExpires() throws InterruptedException {
        OshiSystemMetricsCollector collector = new OshiSystemMetricsCollector(1L, List.of(), List.of(), false);
        SystemMetricsSnapshot first = collector.snapshot();
        Thread.sleep(20L);
        SystemMetricsSnapshot second = collector.snapshot();
        assertThat(second).isNotSameAs(first);
        assertThat(second.getCollectedAt()).isGreaterThanOrEqualTo(first.getCollectedAt());
    }

    @Test
    @DisplayName("指定挂载点后只采集该挂载点")
    void shouldFilterMounts() {
        OshiSystemMetricsCollector all = new OshiSystemMetricsCollector(0L, List.of(), List.of(), false);
        String mount = all.snapshot().getDisk().mounts().get(0).mount();
        OshiSystemMetricsCollector filtered = new OshiSystemMetricsCollector(0L, List.of(mount), List.of(), false);
        assertThat(filtered.snapshot().getDisk().mounts()).extracting(SystemMetricsSnapshot.MountMetrics::mount)
                .containsExactly(mount);
    }
}
