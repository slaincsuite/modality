package cn.slainc.policy.spi;

import cn.slainc.core.enums.LoginType;

import java.util.List;

public interface PolicyGroupProvider {

    List<String> groups(Object loginId, LoginType type);
}
