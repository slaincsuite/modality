package cn.slainc.security.spi;

import cn.slainc.core.enums.LoginType;

import java.util.List;

public interface SecurityPermissionFilter {

    List<String> filter(Object loginId, LoginType type, List<String> permissions);
}
