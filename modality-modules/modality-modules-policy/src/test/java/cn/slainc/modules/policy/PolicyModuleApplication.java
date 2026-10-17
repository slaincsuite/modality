package cn.slainc.modules.policy;

import cn.dev33.satoken.stp.StpInterface;
import cn.slainc.core.result.Result;
import cn.slainc.policy.spi.PolicyGroupProvider;
import cn.slainc.security.StpKit;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@SpringBootConfiguration
@EnableAutoConfiguration
public class PolicyModuleApplication {

    @RestController
    public static class PolicyTestController {

        @GetMapping("/policy-test/login")
        public Result<String> login() {
            StpKit.tenantUser().login(92001L);
            return Result.ok(StpKit.tenantUser().getTokenValue());
        }
    }

    // 无 Redis 环境没有 SecurityPermissionCache，直接以 sa-token 的 StpInterface 提供测试权限
    @Bean
    public StpInterface policyTestPermissionProvider() {
        return new StpInterface() {

            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return List.of("*");
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return List.of("batch11-role");
            }
        };
    }

    @Bean
    public PolicyGroupProvider policyTestGroupProvider() {
        return (loginId, type) -> List.of("group-92001");
    }
}
