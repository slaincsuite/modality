package cn.slainc.security.stp;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.slainc.security.config.SecurityKeys;

public class SecurityStpLogic extends StpLogic {

    public SecurityStpLogic(String loginType) {
        super(loginType);
    }

    public void login(Object loginId, SaLoginParameter parameter) {
        super.login(loginId, parameter);
    }

    public void logoutAndClear(Object loginId) {
        logout(loginId);
        getSaTokenDao().delete(SecurityKeys.lastIpKey(getLoginType(), loginId));
        getSaTokenDao().delete(SecurityKeys.lockFailKey(getLoginType(), loginId));
        getSaTokenDao().delete(SecurityKeys.lockLockedKey(getLoginType(), loginId));
    }
}
