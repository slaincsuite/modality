package cn.slainc.modules.infra;

import cn.dev33.satoken.stp.StpInterface;
import cn.slainc.core.result.Result;
import cn.slainc.monitor.alert.AlertEvent;
import cn.slainc.monitor.alert.AlertLevel;
import cn.slainc.monitor.alert.AlertNotifier;
import cn.slainc.security.StpKit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootConfiguration
@EnableAutoConfiguration
public class InfraModuleApplication {

    public static final List<AlertEvent> RECEIVED_ALERTS = new CopyOnWriteArrayList<>();

    @RestController
    public static class InfraTestController {

        @GetMapping("/infra-test/login")
        public Result<String> login(@org.springframework.web.bind.annotation.RequestParam(required = false) Long userId) {
            StpKit.tenantUser().login(userId == null ? 94001L : userId);
            return Result.ok(StpKit.tenantUser().getTokenValue());
        }
    }

    // 无 Redis 环境没有 SecurityPermissionCache，直接以 sa-token 的 StpInterface 提供测试权限
    @Bean
    public StpInterface infraTestPermissionProvider() {
        return new StpInterface() {

            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return List.of("*");
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return List.of();
            }
        };
    }

    @Bean
    public AlertNotifier infraTestAlertNotifier() {
        return new AlertNotifier() {

            @Override
            public String name() {
                return "infra-test";
            }

            @Override
            public void send(AlertEvent event) {
                RECEIVED_ALERTS.add(event);
            }

            @Override
            public AlertLevel minLevel() {
                return AlertLevel.INFO;
            }
        };
    }
}
