package cn.slainc.modules.policy.web.self;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.policy.dto.MyPolicyView;
import cn.slainc.modules.policy.service.self.PolicySelfService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "我的策略", description = "当前登录账号直接、角色和用户组关联策略")
@RestController
@RequestMapping("/auth/me")
public class PolicySelfController {

    private final PolicySelfService selfService;

    public PolicySelfController(PolicySelfService selfService) {
        this.selfService = selfService;
    }

    @GetMapping("/policies")
    public MyPolicyView policies() {
        UserContext.UserInfo user = UserContext.get();
        if (user == null || user.userId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "当前请求未认证，无法读取账号策略");
        }
        return selfService.policies(user);
    }
}
