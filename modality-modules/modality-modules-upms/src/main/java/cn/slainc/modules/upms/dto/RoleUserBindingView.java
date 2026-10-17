package cn.slainc.modules.upms.dto;

import cn.slainc.modules.upms.entity.UserRecord;

public record RoleUserBindingView(UserRecord user, Long boundAt) {
}
