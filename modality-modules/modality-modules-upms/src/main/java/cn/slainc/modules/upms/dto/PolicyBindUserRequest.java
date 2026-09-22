package cn.slainc.modules.upms.dto;

import cn.slainc.core.enums.LoginType;

public record PolicyBindUserRequest(String policyId, Long userId, LoginType loginType) {
}
