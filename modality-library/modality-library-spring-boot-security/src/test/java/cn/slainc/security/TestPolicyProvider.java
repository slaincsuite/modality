package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.spi.SecurityPolicyProvider;

import java.util.List;

public class TestPolicyProvider implements SecurityPolicyProvider {

    @Override
    public List<String> getPermissions(Object loginId, LoginType type) {
        if (isLimited(loginId, type)) {
            return List.of();
        }
        if (type == LoginType.USER) {
            return List.of("order:write", "order:export");
        }
        return List.of();
    }

    @Override
    public List<String> getRoles(Object loginId, LoginType type) {
        if (isLimited(loginId, type)) {
            return List.of();
        }
        if (type == LoginType.USER) {
            return List.of("operator");
        }
        return List.of();
    }

    private boolean isLimited(Object loginId, LoginType type) {
        return "2999".equals(String.valueOf(loginId)) && type == LoginType.USER;
    }
}
