package cn.slainc.session;

import cn.slainc.core.enums.LoginType;
import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.session.device.DeviceTypeStats;
import cn.slainc.session.metrics.SessionMetricsBinder;
import cn.slainc.session.stats.SessionStats;
import cn.slainc.session.stats.SessionStatsListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionStatsMetricsTest extends SessionTestSupport {

    @Autowired
    private MetricsTemplate metricsTemplate;

    @Autowired
    private SessionMetricsBinder metricsBinder;

    @Test
    @DisplayName("统计快照给出总数、体系、设备与租户维度")
    void statsSnapshotDimensions() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web", "stats-a", "127.0.0.1", null, "1001");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "app-android", "stats-b", "127.0.0.1", null, "1001");
        loginDirect(LoginType.PLATFORM_ADMIN, ADMIN_ONE, "web", "stats-c", "127.0.0.1", null, null);

        SessionStats stats = statsService.snapshot(null);

        assertThat(stats.total()).isEqualTo(3);
        assertThat(stats.byType()).containsEntry("TENANT_USER", 2L).containsEntry("PLATFORM_ADMIN", 1L);
        assertThat(stats.byDeviceType()).containsEntry("PC", 2L).containsEntry("MOBILE", 1L);
        assertThat(stats.byTenant()).containsEntry("1001", 2L).containsKey(null);
        assertThat(stats.truncated()).isFalse();
    }

    @Test
    @DisplayName("设备维度统计固定给出四类分桶")
    void deviceDimensionStatsBucketed() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "app-ios");
        loginDirect(LoginType.TENANT_USER, USER_THREE, "ipad");

        DeviceTypeStats stats = statsService.deviceTypeStats(LoginType.TENANT_USER);

        assertThat(stats.total()).isEqualTo(3);
        assertThat(stats.counts()).containsEntry("PC", 1L).containsEntry("MOBILE", 1L).containsEntry("TABLET", 1L)
                .containsEntry("UNKNOWN", 0L);
    }

    @Test
    @DisplayName("统计平均在线时长")
    void averageOnlineDurationStats() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        extender.setExtra(LoginType.TENANT_USER, token, "loginTime", System.currentTimeMillis() - 60_000L);

        SessionStats stats = statsService.snapshot(LoginType.TENANT_USER);

        assertThat(stats.avgDurationSeconds()).isGreaterThan(50d).isLessThan(120d);
    }

    @Test
    @DisplayName("在线数量与统计快照总数一致")
    void onlineCountMatchesSnapshot() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web");

        assertThat(statsService.onlineCount(LoginType.TENANT_USER)).isEqualTo(2);
        assertThat(statsService.onlineCount(LoginType.APP)).isZero();
    }

    @Test
    @DisplayName("统计服务提供体系、设备、租户三种聚合")
    void statsAggregationMethods() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.APP, CLIENT_ONE, "app-ios");

        assertThat(statsService.countByType()).containsEntry("TENANT_USER", 1L).containsEntry("APP", 1L);
        assertThat(statsService.countByDeviceType()).containsEntry("PC", 1L).containsEntry("MOBILE", 1L);
        Map<String, Long> tenants = statsService.countByTenant();
        assertThat(tenants).containsKey(null);
        assertThat(tenants.get(null)).isEqualTo(2L);
    }

    @Test
    @DisplayName("读取统计后在线数指标随体系打标更新")
    void onlineCountMetricsTaggedByType() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web");
        loginDirect(LoginType.APP, CLIENT_ONE, "web");

        statsService.snapshot(null);

        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "TENANT_USER")).isEqualTo(2d);
        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "APP")).isEqualTo(1d);
        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "PLATFORM_ADMIN")).isZero();
    }

    @Test
    @DisplayName("在线数指标给出全部体系合计")
    void onlineCountMetricsSummed() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.PLATFORM_ADMIN, ADMIN_ONE, "web");

        statsService.snapshot(null);

        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "ALL")).isEqualTo(2d);
        assertThat(metricsBinder.online("ALL")).isEqualTo(2d);
    }

    @Test
    @DisplayName("平均在线时长指标可被读取")
    void averageDurationMetrics() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        extender.setExtra(LoginType.TENANT_USER, token, "loginTime", System.currentTimeMillis() - 120_000L);

        metricsBinder.refresh();

        assertThat(metricsTemplate.gaugeValue("modality.session.avg-duration-seconds")).isGreaterThan(100d);
        assertThat(metricsBinder.avgDurationSeconds()).isGreaterThan(100d);
    }

    @Test
    @DisplayName("指定体系统计只统计该体系并驱动指标")
    void statsByTypeWithMetrics() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.PLATFORM_ADMIN, ADMIN_ONE, "web");

        SessionStats stats = statsService.snapshot(LoginType.PLATFORM_ADMIN);

        assertThat(stats.total()).isEqualTo(1);
        assertThat(stats.byType()).containsOnlyKeys("PLATFORM_ADMIN");
        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "PLATFORM_ADMIN")).isEqualTo(1d);
        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "ALL")).isEqualTo(1d);
    }

    @Test
    @DisplayName("会话被踢后在线数指标归零")
    void metricsZeroedAfterKick() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        statsService.snapshot(LoginType.TENANT_USER);
        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "TENANT_USER")).isEqualTo(1d);

        kickSessionService.kickoutByToken(LoginType.TENANT_USER, token, "指标校验");
        statsService.snapshot(LoginType.TENANT_USER);

        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "TENANT_USER")).isZero();
    }

    @Test
    @DisplayName("统计截断标记与中文说明可透出")
    void statsTruncationFlag() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web");
        config.set("modality.session.max-scan", "1");

        SessionStats stats = statsService.snapshot(LoginType.TENANT_USER);

        assertThat(stats.truncated()).isTrue();
        assertThat(stats.message()).contains("扫描已达上限");
        assertThat(stats.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("指标绑定器实现统计监听器并可导出快照")
    void metricsBinderSnapshot() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        statsService.snapshot(null);

        assertThat(metricsBinder).isInstanceOf(SessionStatsListener.class);
        assertThat(metricsBinder.snapshot()).containsKeys("TENANT_USER", "ALL", "avgDurationSeconds");
    }

    @Test
    @DisplayName("无在线会话时指标为零")
    void metricsZeroWithoutSession() {
        SessionStats stats = statsService.snapshot(null);

        assertThat(stats.total()).isZero();
        assertThat(stats.avgDurationSeconds()).isZero();
        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "ALL")).isZero();
    }
}
