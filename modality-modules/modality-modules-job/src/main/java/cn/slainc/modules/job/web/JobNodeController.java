package cn.slainc.modules.job.web;

import cn.slainc.core.result.Result;
import cn.slainc.modules.job.JobKeys;
import cn.slainc.modules.job.support.NodeRegistry;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "任务节点", description = "执行节点心跳状态")
@RestController
@RequestMapping("/job/node")
public class JobNodeController {

    private final NodeRegistry nodeRegistry;

    public JobNodeController(NodeRegistry nodeRegistry) {
        this.nodeRegistry = nodeRegistry;
    }

    @Operation(summary = "列出集群节点")
    @GetMapping
    @RequirePermission(permissions = JobKeys.PERMISSION_NODE_QUERY)
    public Result<Map<String, String>> nodes() {
        return Result.ok(nodeRegistry.nodeStatus());
    }
}
