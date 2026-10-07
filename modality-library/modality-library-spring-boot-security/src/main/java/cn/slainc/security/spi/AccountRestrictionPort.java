package cn.slainc.security.spi;

import cn.slainc.core.enums.LoginType;

import java.util.List;

public interface AccountRestrictionPort {

    List<String> validate(String username, LoginType type, String clientIp, String deviceType);
}
