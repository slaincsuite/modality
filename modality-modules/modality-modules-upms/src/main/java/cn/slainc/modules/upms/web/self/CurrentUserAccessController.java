package cn.slainc.modules.upms.web.self;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.entity.UserGroupRecord;
import cn.slainc.modules.upms.service.group.UserGroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "当前账号权限", description = "当前登录账号的权限码与用户组")
@RestController
@RequestMapping("/auth/me")
public class CurrentUserAccessController {

    private final UserGroupService groupService;

    public CurrentUserAccessController(UserGroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/permissions")
    public List<String> permissions() {
        requireLogin();
        return UserContext.permissions().stream()
                .filter(permission -> !permission.startsWith("!"))
                .toList();
    }

    @GetMapping("/groups")
    public List<UserGroupRecord> groups() {
        requireLogin();
        return groupService.groupsOf(UserContext.userId());
    }

    private void requireLogin() {
        if (UserContext.userId() == null) {
            throw new BizException("登录状态已失效，请重新登录");
        }
    }
}
