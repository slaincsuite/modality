package cn.slainc.boot;

import cn.slainc.core.result.Result;
import cn.slainc.policy.aspect.RequirePolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 批次 11 验收探针：真实 HTTP 上验证策略修改即时生效
@RestController
public class Batch11AcceptanceController {

    @RequirePolicy(action = "modality:batch11:data:read", resource = "modality:batch11/data")
    @GetMapping("/batch11/policy-probe")
    public Result<String> policyProbe() {
        return Result.ok("batch11-policy-ok");
    }
}
