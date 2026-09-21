package cn.slainc.boot;

import cn.slainc.core.result.Result;
import cn.slainc.policy.aspect.RequirePolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Batch6AcceptanceController {

    @RequirePolicy(action = "modality:batch6:data:read", resource = "modality:batch6/data")
    @GetMapping("/batch6/read")
    public Result<String> read() {
        return Result.ok("read-ok");
    }

    @RequirePolicy(action = "modality:batch6:data:write", resource = "modality:batch6/data")
    @GetMapping("/batch6/write")
    public Result<String> write() {
        return Result.ok("write-ok");
    }

    @RequirePolicy(action = "modality:batch6:data:purge", resource = "modality:batch6/data")
    @GetMapping("/batch6/unmanaged")
    public Result<String> unmanaged() {
        return Result.ok("purge-ok");
    }
}
