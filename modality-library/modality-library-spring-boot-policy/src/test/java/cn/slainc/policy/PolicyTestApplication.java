package cn.slainc.policy;

import cn.slainc.policy.aspect.RequirePolicy;
import cn.slainc.core.result.Result;
import cn.slainc.policy.spi.PolicyGroupProvider;
import cn.slainc.security.StpKit;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@SpringBootConfiguration
@EnableAutoConfiguration
public class PolicyTestApplication {

    @RestController
    public static class PolicyTestController {

        @GetMapping("/policy-test/login")
        public Result<String> login() {
            StpKit.tenantUser().login(10001L);
            return Result.ok(StpKit.tenantUser().getTokenValue());
        }

        @GetMapping("/policy-test/login-admin")
        public Result<String> loginAdmin() {
            StpKit.platformAdmin().login(20001L);
            return Result.ok(StpKit.platformAdmin().getTokenValue());
        }

        @GetMapping("/policy-test/open-safe")
        public Result<Boolean> openSafe() {
            StpKit.tenantUser().openSafe("DEFAULT", 60);
            return Result.ok(true);
        }

        @RequirePolicy(action = "modality:policy-test:read", resource = "modality:policy-test/data")
        @GetMapping("/policy-test/read")
        public Result<String> read() {
            return Result.ok("read-ok");
        }

        @RequirePolicy(action = "modality:policy-test:write", resource = "modality:policy-test/data")
        @GetMapping("/policy-test/write")
        public Result<String> write() {
            return Result.ok("write-ok");
        }

        @RequirePolicy(action = "modality:policy-test:doc", resource = "modality:doc/{docId}")
        @GetMapping("/policy-test/doc/{docId}")
        public Result<String> doc(@PathVariable String docId) {
            return Result.ok("doc-" + docId);
        }

        @RequirePolicy(action = "modality:policy-test:group", resource = "modality:policy-test/group")
        @GetMapping("/policy-test/group")
        public Result<String> group() {
            return Result.ok("group-ok");
        }

        @RequirePolicy(action = "modality:policy-test:mfa", resource = "modality:policy-test/mfa")
        @GetMapping("/policy-test/mfa")
        public Result<String> mfa() {
            return Result.ok("mfa-ok");
        }
    }

    @Bean
    public PolicyGroupProvider policyGroupProvider() {
        return (loginId, type) -> List.of("group-10001");
    }
}
