package cn.slainc.policy;

import cn.slainc.policy.aspect.RequirePolicy;
import cn.slainc.core.result.Result;
import cn.slainc.security.StpKit;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootConfiguration
@EnableAutoConfiguration
public class PolicyTestApplication {

    @RestController
    public static class PolicyTestController {

        @GetMapping("/policy-test/login")
        public Result<String> login() {
            StpKit.user().login(10001L);
            return Result.ok(StpKit.user().getTokenValue());
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
    }
}
