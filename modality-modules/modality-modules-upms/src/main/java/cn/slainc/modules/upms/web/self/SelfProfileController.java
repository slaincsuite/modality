package cn.slainc.modules.upms.web.self;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.upms.dto.AvatarRequest;
import cn.slainc.modules.upms.dto.SelfProfileView;
import cn.slainc.modules.upms.dto.SelfProfileUpdateRequest;
import cn.slainc.modules.upms.service.user.UserService;
import cn.slainc.security.annotation.RequireLogin;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "当前账号资料", description = "当前登录账号自助查看与修改个人资料")
@RestController
@RequestMapping("/upms/me/profile")
public class SelfProfileController {

    private final UserService userService;

    public SelfProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @RequireLogin
    public SelfProfileView get() {
        return SelfProfileView.of(userService.get(currentUserId()));
    }

    @PutMapping
    @RequireLogin
    @AuditLog(value = "修改个人资料", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "upms", recordParams = false)
    public SelfProfileView update(@RequestBody SelfProfileUpdateRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "个人资料不能为空");
        }
        return SelfProfileView.of(userService.update(currentUserId(), request.nickname(), request.email(),
                request.phone(), null, null, null, null, null, null, null, request.remark()));
    }

    @PutMapping("/avatar")
    @RequireLogin
    @AuditLog(value = "修改个人头像", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "upms")
    public void updateAvatar(@RequestBody AvatarRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "头像地址不能为空");
        }
        userService.setAvatar(currentUserId(), request.avatarUrl());
    }

    private Long currentUserId() {
        Long userId = UserContext.userId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "当前请求未登录，无法读取账号资料");
        }
        return userId;
    }
}
