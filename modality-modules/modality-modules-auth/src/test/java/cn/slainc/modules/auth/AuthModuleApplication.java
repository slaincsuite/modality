package cn.slainc.modules.auth;

import cn.slainc.core.result.Result;
import cn.slainc.security.StpKit;
import cn.slainc.security.annotation.RequireLogin;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootConfiguration
@EnableAutoConfiguration
public class AuthModuleApplication {

    @Bean
    public FakeWechatProvider wechatAuthProvider() {
        return new FakeWechatProvider();
    }

    @RestController
    static class ProbeController {

        @GetMapping("/auth/test/me")
        @RequireLogin
        public Result<Object> me() {
            return Result.ok(StpKit.tenantUser().getLoginId());
        }
    }
}
