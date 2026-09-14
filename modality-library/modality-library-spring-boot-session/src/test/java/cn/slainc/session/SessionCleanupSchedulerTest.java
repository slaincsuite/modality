package cn.slainc.session;

import cn.slainc.core.enums.LoginType;
import cn.slainc.session.cleanup.CleanupReport;
import cn.slainc.session.cleanup.SessionCleanupScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCleanupSchedulerTest extends SessionCleanupEnabledTestSupport {

    @Autowired
    private SessionCleanupScheduler scheduler;

    @Test
    @DisplayName("开启清理后调度器处于运行状态")
    void 调度器运行() {
        assertThat(scheduler.isRunning()).isTrue();
    }

    @Test
    @DisplayName("首次调度延迟一个周期")
    void 首次延迟一个周期() {
        assertThat(scheduler.initialDelayMillis()).isEqualTo(30 * 60_000L);
        assertThat(scheduler.nextDelayMillis()).isEqualTo(30 * 60_000L);
    }

    @Test
    @DisplayName("热配置改变周期后调度间隔随之变化")
    void 周期热配置() {
        config.set("modality.session.cleanup.interval-minutes", "5");

        assertThat(scheduler.nextDelayMillis()).isEqualTo(5 * 60_000L);
    }

    @Test
    @DisplayName("手动触发一次清理会清理残留终端")
    void 手动触发清理() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web");
        deleteKey("satoken:USER:token:" + token);

        CleanupReport report = scheduler.runOnce();

        assertThat(report.removedTerminals()).isEqualTo(1);
    }

    @Test
    @DisplayName("调度线程为单线程守护线程")
    void 调度线程为守护线程() {
        boolean daemon = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> SessionCleanupScheduler.THREAD_NAME.equals(thread.getName()))
                .allMatch(Thread::isDaemon);
        boolean present = Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> SessionCleanupScheduler.THREAD_NAME.equals(thread.getName()));

        assertThat(present).isTrue();
        assertThat(daemon).isTrue();
    }
}
