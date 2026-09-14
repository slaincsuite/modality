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
    void 统计快照维度() {
        loginDirect(LoginType.USER, USER_ONE, "web", "stats-a", "127.0.0.1", null, "1001");
        loginDirect(LoginType.USER, USER_TWO, "app-android", "stats-b", "127.0.0.1", null, "1001");
        loginDirect(LoginType.ADMIN, ADMIN_ONE, "web", "stats-c", "127.0.0.1", null, null);

        SessionStats stats = statsService.snapshot(null);

        assertThat(stats.total()).isEqualTo(3);
        assertThat(stats.byType()).containsEntry("USER", 2L).containsEntry("ADMIN", 1L);
        assertThat(stats.byDeviceType()).containsEntry("PC", 2L).containsEntry("MOBILE", 1L);
        assertThat(stats.byTenant()).containsEntry("1001", 2L).containsKey(null);
        assertThat(stats.truncated()).isFalse();
    }

    @Test
    @DisplayName("设备维度统计固定给出四类分桶")
    void 设备维度统计分桶() {
        loginDirect(LoginType.USER, USER_ONE, "web");
        loginDirect(LoginType.USER, USER_TWO, "app-ios");
        loginDirect(LoginType.USER, USER_THREE, "ipad");

        DeviceTypeStats stats = statsService.deviceTypeStats(LoginType.USER);

        assertThat(stats.total()).isEqualTo(3);
        assertThat(stats.counts()).containsEntry("PC", 1L).containsEntry("MOBILE", 1L).containsEntry("TABLET", 1L)
                .containsEntry("UNKNOWN", 0L);
    }

    @Test
    @DisplayName("统计平均在线时长")
    void 统计平均在线时长() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web");
        extender.setExtra(LoginType.USER, token, "loginTime", System.currentTimeMillis() - 60_000L);

        SessionStats stats = statsService.snapshot(LoginType.USER);

        assertThat(stats.avgDurationSeconds()).isGreaterThan(50d).isLessThan(120d);
    }

    @Test
    @DisplayName("在线数量与统计快照总数一致")
    void 在线数量与快照一致() {
        loginDirect(LoginType.USER, USER_ONE, "web");
        loginDirect(LoginType.USER, USER_TWO, "web");

        assertThat(statsService.onlineCount(LoginType.USER)).isEqualTo(2);
        assertThat(statsService.onlineCount(LoginType.CLIENT)).isZero();
    }

    @Test
    @DisplayName("统计服务提供体系、设备、租户三种聚合")
    void 统计聚合方法() {
        loginDirect(LoginType.USER, USER_ONE, "web");
        loginDirect(LoginType.CLIENT, CLIENT_ONE, "app-ios");

        assertThat(statsService.countByType()).containsEntry("USER", 1L).containsEntry("CLIENT", 1L);
        assertThat(statsService.countByDeviceType()).containsEntry("PC", 1L).containsEntry("MOBILE", 1L);
        Map<String, Long> tenants = statsService.countByTenant();
        assertThat(tenants).containsKey(null);
        assertThat(tenants.get(null)).isEqualTo(2L);
    }

    @Test
    @DisplayName("读取统计后在线数指标随体系打标更新")
    void 在线数指标按体系打标() {
        loginDirect(LoginType.USER, USER_ONE, "web");
        loginDirect(LoginType.USER, USER_TWO, "web");
        loginDirect(LoginType.CLIENT, CLIENT_ONE, "web");

        statsService.snapshot(null);

        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "USER")).isEqualTo(2d);
        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "CLIENT")).isEqualTo(1d);
        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "ADMIN")).isZero();
    }

    @Test
    @DisplayName("在线数指标给出全部体系合计")
    void 在线数指标合计() {
        loginDirect(LoginType.USER, USER_ONE, "web");
        loginDirect(LoginType.ADMIN, ADMIN_ONE, "web");

        statsService.snapshot(null);

        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "ALL")).isEqualTo(2d);
        assertThat(metricsBinder.online("ALL")).isEqualTo(2d);
    }

    @Test
    @DisplayName("平均在线时长指标可被读取")
    void 平均时长指标() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web");
        extender.setExtra(LoginType.USER, token, "loginTime", System.currentTimeMillis() - 120_000L);

        metricsBinder.refresh();

        assertThat(metricsTemplate.gaugeValue("modality.session.avg-duration-seconds")).isGreaterThan(100d);
        assertThat(metricsBinder.avgDurationSeconds()).isGreaterThan(100d);
    }

    @Test
    @DisplayName("指定体系统计只统计该体系并驱动指标")
    void 指定体系统计与指标() {
        loginDirect(LoginType.USER, USER_ONE, "web");
        loginDirect(LoginType.ADMIN, ADMIN_ONE, "web");

        SessionStats stats = statsService.snapshot(LoginType.ADMIN);

        assertThat(stats.total()).isEqualTo(1);
        assertThat(stats.byType()).containsOnlyKeys("ADMIN");
        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "ADMIN")).isEqualTo(1d);
        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "ALL")).isEqualTo(1d);
    }

    @Test
    @DisplayName("会话被踢后在线数指标归零")
    void 踢人后指标归零() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web");
        statsService.snapshot(LoginType.USER);
        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "USER")).isEqualTo(1d);

        kickSessionService.kickoutByToken(LoginType.USER, token, "指标校验");
        statsService.snapshot(LoginType.USER);

        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "USER")).isZero();
    }

    @Test
    @DisplayName("统计截断标记与中文说明可透出")
    void 统计截断标记() {
        loginDirect(LoginType.USER, USER_ONE, "web");
        loginDirect(LoginType.USER, USER_TWO, "web");
        config.set("modality.session.max-scan", "1");

        SessionStats stats = statsService.snapshot(LoginType.USER);

        assertThat(stats.truncated()).isTrue();
        assertThat(stats.message()).contains("扫描已达上限");
        assertThat(stats.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("指标绑定器实现统计监听器并可导出快照")
    void 指标绑定器快照() {
        loginDirect(LoginType.USER, USER_ONE, "web");
        statsService.snapshot(null);

        assertThat(metricsBinder).isInstanceOf(SessionStatsListener.class);
        assertThat(metricsBinder.snapshot()).containsKeys("USER", "ALL", "avgDurationSeconds");
    }

    @Test
    @DisplayName("无在线会话时指标为零")
    void 无会话时指标为零() {
        SessionStats stats = statsService.snapshot(null);

        assertThat(stats.total()).isZero();
        assertThat(stats.avgDurationSeconds()).isZero();
        assertThat(metricsTemplate.gaugeValue("modality.session.online", "type", "ALL")).isZero();
    }
}
