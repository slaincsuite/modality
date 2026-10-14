package cn.slainc.modules.tenant;

import cn.dev33.satoken.stp.StpInterface;
import cn.slainc.core.result.Result;
import cn.slainc.security.StpKit;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@SpringBootConfiguration
@EnableAutoConfiguration
public class TenantModuleApplication {

    @RestController
    public static class TenantTestController {

        @GetMapping("/tenant-test/login")
        public Result<String> login() {
            StpKit.tenantUser().login(93001L);
            return Result.ok(StpKit.tenantUser().getTokenValue());
        }
    }

    // 无 Redis 环境没有 SecurityPermissionCache，直接以 sa-token 的 StpInterface 提供测试权限
    @Bean
    public StpInterface tenantTestPermissionProvider() {
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
}
