package cn.slainc.modules.upms.dto;

import cn.slainc.modules.upms.entity.UserGroupRecord;

public record UserGroupBindingView(UserGroupRecord group, Long joinedAt) {
}
