package cn.slainc.security.spi;

import cn.slainc.core.enums.LoginType;

import java.util.List;

public interface SecurityPermissionProvider {

    List<String> getPermissions(Object loginId, LoginType type);

    List<String> getRoles(Object loginId, LoginType type);
}
