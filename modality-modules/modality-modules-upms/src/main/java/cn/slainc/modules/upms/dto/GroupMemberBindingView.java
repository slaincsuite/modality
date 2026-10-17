package cn.slainc.modules.upms.dto;

import cn.slainc.modules.upms.entity.UserRecord;

public record GroupMemberBindingView(UserRecord user, Long joinedAt) {
}
