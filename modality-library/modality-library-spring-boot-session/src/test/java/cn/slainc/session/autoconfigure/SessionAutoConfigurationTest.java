package cn.slainc.session.autoconfigure;

import cn.slainc.monitor.metrics.MetricNaming;
import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.security.audit.SecurityAuditRecorder;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.security.config.SecurityProperties;
import cn.slainc.security.kick.KickService;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.session.cleanup.SessionCleanupScheduler;
import cn.slainc.session.cleanup.SessionCleanupService;
import cn.slainc.session.config.SessionConfigReader;
import cn.slainc.session.extend.SessionExtender;
import cn.slainc.session.extend.SessionRenewalService;
import cn.slainc.session.kick.KickSessionService;
import cn.slainc.session.kick.SessionKickNotifier;
import cn.slainc.session.kick.Slf4jSessionKickNotifier;
import cn.slainc.session.kick.WebSocketSessionKickNotifier;
import cn.slainc.session.limit.SessionLimitService;
import cn.slainc.session.limit.SessionLoginListener;
import cn.slainc.session.metrics.SessionMetricsBinder;
import cn.slainc.session.online.OnlineUserService;
import cn.slainc.session.query.SessionQueryService;
import cn.slainc.session.query.SessionScanner;
import cn.slainc.session.stats.SessionStatsService;
import cn.slainc.session.timeout.SessionTimeoutPolicy;
import cn.slainc.web.websocket.WebSocketBroadcaster;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SessionAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SessionAutoConfiguration.class))
            .withBean(SecurityConfigReader.class, () -> new SecurityConfigReader(new SecurityProperties(),
                    emptyProvider()));

    private ApplicationContextRunner kickRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SessionAutoConfiguration.class,
                        SessionKickAutoConfiguration.class))
                .withBean(SecurityConfigReader.class, () -> new SecurityConfigReader(new SecurityProperties(),
                        emptyProvider()))
                .withBean(KickService.class, () -> Mockito.mock(KickService.class))
                .withBean(SecurityLoginService.class, () -> Mockito.mock(SecurityLoginService.class));
    }

    private ApplicationContextRunner websocketRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SessionAutoConfiguration.class,
                        SessionKickAutoConfiguration.class, SessionWebSocketNotifyAutoConfiguration.class))
                .withBean(SecurityConfigReader.class, () -> new SecurityConfigReader(new SecurityProperties(),
                        emptyProvider()))
                .withBean(KickService.class, () -> Mockito.mock(KickService.class))
                .withBean(SecurityLoginService.class, () -> Mockito.mock(SecurityLoginService.class));
    }

    private ApplicationContextRunner cleanupRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SessionAutoConfiguration.class,
                        SessionCleanupAutoConfiguration.class))
                .withBean(SecurityConfigReader.class, () -> new SecurityConfigReader(new SecurityProperties(),
                        emptyProvider()))
                .withBean(KickService.class, () -> Mockito.mock(KickService.class))
                .withBean(SecurityAuditRecorder.class, () -> new SecurityAuditRecorder(event -> {
                }));
    }

    private ApplicationContextRunner metricsRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SessionAutoConfiguration.class,
                        SessionMetricsAutoConfiguration.class))
                .withBean(SecurityConfigReader.class, () -> new SecurityConfigReader(new SecurityProperties(),
                        emptyProvider()))
                .withBean(MetricsTemplate.class, () -> new MetricsTemplate(new SimpleMeterRegistry(),
                        new MetricNaming(), false));
    }

    @Test
    @DisplayName("默认注册全部会话服务")
    void 默认注册全部会话服务() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SessionConfigReader.class);
            assertThat(context).hasSingleBean(SessionScanner.class);
            assertThat(context).hasSingleBean(SessionTimeoutPolicy.class);
            assertThat(context).hasSingleBean(SessionQueryService.class);
            assertThat(context).hasSingleBean(OnlineUserService.class);
            assertThat(context).hasSingleBean(SessionStatsService.class);
            assertThat(context).hasSingleBean(SessionExtender.class);
            assertThat(context).hasSingleBean(SessionRenewalService.class);
        });
    }

    @Test
    @DisplayName("modality.session.enabled=false 时不注册任何会话服务")
    void 总开关关闭() {
        runner.withPropertyValues("modality.session.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SessionConfigReader.class);
            assertThat(context).doesNotHaveBean(SessionQueryService.class);
            assertThat(context).doesNotHaveBean(OnlineUserService.class);
        });
    }

    @Test
    @DisplayName("query.enabled=false 只关闭查询服务")
    void 关闭查询能力() {
        runner.withPropertyValues("modality.session.query.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(SessionQueryService.class);
            assertThat(context).hasSingleBean(OnlineUserService.class);
            assertThat(context).hasSingleBean(SessionStatsService.class);
        });
    }

    @Test
    @DisplayName("online.enabled=false 只关闭在线列表能力")
    void 关闭在线能力() {
        runner.withPropertyValues("modality.session.online.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(OnlineUserService.class);
            assertThat(context).hasSingleBean(SessionQueryService.class);
        });
    }

    @Test
    @DisplayName("stats.enabled=false 只关闭统计能力")
    void 关闭统计能力() {
        runner.withPropertyValues("modality.session.stats.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(SessionStatsService.class);
            assertThat(context).hasSingleBean(OnlineUserService.class);
        });
    }

    @Test
    @DisplayName("extend.enabled=false 只关闭扩展能力")
    void 关闭扩展能力() {
        runner.withPropertyValues("modality.session.extend.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(SessionExtender.class);
            assertThat(context).hasSingleBean(SessionRenewalService.class);
        });
    }

    @Test
    @DisplayName("renew.enabled=false 只关闭续期能力")
    void 关闭续期能力() {
        runner.withPropertyValues("modality.session.renew.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(SessionRenewalService.class);
            assertThat(context).hasSingleBean(SessionExtender.class);
        });
    }

    @Test
    @DisplayName("使用方可以覆盖默认服务 Bean")
    void 允许覆盖默认Bean() {
        runner.withBean(SessionExtender.class, SessionExtender::new).run(context -> {
            assertThat(context).hasSingleBean(SessionExtender.class);
        });
    }

    @Test
    @DisplayName("类路径缺少监控依赖时指标配置整体退避")
    void 缺少监控依赖退避() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SessionAutoConfiguration.class,
                        SessionMetricsAutoConfiguration.class))
                .withBean(SecurityConfigReader.class, () -> new SecurityConfigReader(new SecurityProperties(),
                        emptyProvider()))
                .withClassLoader(new FilteredClassLoader("cn.slainc.monitor.metrics"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SessionMetricsBinder.class);
                    assertThat(context).hasSingleBean(SessionStatsService.class);
                });
    }

    @Test
    @DisplayName("存在 MetricsTemplate 时注册指标绑定器")
    void 注册指标绑定器() {
        metricsRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SessionMetricsBinder.class);
        });
    }

    @Test
    @DisplayName("metrics.enabled=false 时不注册指标绑定器")
    void 关闭指标绑定器() {
        metricsRunner().withPropertyValues("modality.session.metrics.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(SessionMetricsBinder.class);
            assertThat(context).hasSingleBean(SessionStatsService.class);
        });
    }

    @Test
    @DisplayName("类路径缺少 WebSocket 时通知配置退避")
    void 缺少WebSocket退避() {
        websocketRunner().withClassLoader(new FilteredClassLoader("cn.slainc.web.websocket.WebSocketBroadcaster"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(WebSocketSessionKickNotifier.class);
                    assertThat(context).hasSingleBean(Slf4jSessionKickNotifier.class);
                });
    }

    @Test
    @DisplayName("存在 WebSocketBroadcaster 时注册 WebSocket 通知器")
    void 注册WebSocket通知器() {
        websocketRunner().withBean(WebSocketBroadcaster.class, () -> Mockito.mock(WebSocketBroadcaster.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WebSocketSessionKickNotifier.class);
                    assertThat(context).hasSingleBean(KickSessionService.class);
                });
    }

    @Test
    @DisplayName("没有自定义通知器时注册 Slf4j 默认实现")
    void 注册默认通知器() {
        kickRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(Slf4jSessionKickNotifier.class);
            assertThat(context.getBean(SessionKickNotifier.class))
                    .isInstanceOf(Slf4jSessionKickNotifier.class);
        });
    }

    @Test
    @DisplayName("notify.enabled=false 时不注册默认通知器")
    void 关闭通知能力() {
        kickRunner().withPropertyValues("modality.session.notify.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(Slf4jSessionKickNotifier.class);
            assertThat(context).hasSingleBean(KickSessionService.class);
        });
    }

    @Test
    @DisplayName("kick.enabled=false 时不注册踢人相关能力")
    void 关闭踢人能力() {
        kickRunner().withPropertyValues("modality.session.kick.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(KickSessionService.class);
            assertThat(context).doesNotHaveBean(SessionLimitService.class);
            assertThat(context).doesNotHaveBean(Slf4jSessionKickNotifier.class);
        });
    }

    @Test
    @DisplayName("默认注册踢人服务与会话上限服务")
    void 注册踢人与上限服务() {
        kickRunner().run(context -> {
            assertThat(context).hasSingleBean(KickSessionService.class);
            assertThat(context).hasSingleBean(SessionLimitService.class);
            assertThat(context).hasSingleBean(SessionLoginListener.class);
        });
    }

    @Test
    @DisplayName("limit.enabled=false 时不注册上限服务与登录监听")
    void 关闭上限能力() {
        kickRunner().withPropertyValues("modality.session.limit.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(KickSessionService.class);
            assertThat(context).doesNotHaveBean(SessionLimitService.class);
            assertThat(context).doesNotHaveBean(SessionLoginListener.class);
        });
    }

    @Test
    @DisplayName("清理能力默认关闭")
    void 清理默认关闭() {
        cleanupRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SessionCleanupService.class);
            assertThat(context).doesNotHaveBean(SessionCleanupScheduler.class);
        });
    }

    @Test
    @DisplayName("cleanup.enabled=true 时注册清理服务与调度器")
    void 开启清理能力() {
        cleanupRunner().withPropertyValues("modality.session.cleanup.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SessionCleanupService.class);
            assertThat(context).hasSingleBean(SessionCleanupScheduler.class);
        });
    }

    @Test
    @DisplayName("会话配置属性可按前缀绑定")
    void 配置属性绑定() {
        runner.withPropertyValues("modality.session.max-scan=88", "modality.session.max-lifetime-seconds=99",
                "modality.session.display.mask-token=false", "modality.session.cleanup.interval-minutes=7").run(
                context -> {
                    assertThat(context).hasNotFailed();
                    SessionConfigReader reader = context.getBean(SessionConfigReader.class);
                    assertThat(reader.maxScan()).isEqualTo(88);
                    assertThat(reader.maxLifetimeSeconds()).isEqualTo(99L);
                    assertThat(reader.maskToken()).isFalse();
                    assertThat(reader.cleanupIntervalMinutes()).isEqualTo(7);
                });
    }

    @Test
    @DisplayName("默认配置值符合约定")
    void 默认配置值() {
        runner.run(context -> {
            SessionConfigReader reader = context.getBean(SessionConfigReader.class);
            assertThat(reader.maxScan()).isEqualTo(10000);
            assertThat(reader.maxLifetimeSeconds()).isEqualTo(2592000L);
            assertThat(reader.maskToken()).isTrue();
            assertThat(reader.cleanupIntervalMinutes()).isEqualTo(30);
            assertThat(reader.maxSessionsPerUser()).isZero();
            assertThat(reader.idleTimeoutSeconds()).isZero();
        });
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return null;
            }
        };
    }
}
