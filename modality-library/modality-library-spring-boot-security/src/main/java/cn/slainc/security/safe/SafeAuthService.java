package cn.slainc.security.safe;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.StpKit;

public class SafeAuthService {

    public void openSafe(LoginType type, String service, long safeTimeSeconds) {
        StpKit.of(type).openSafe(service, safeTimeSeconds);
    }

    public boolean isSafe(LoginType type, String service) {
        return StpKit.of(type).isSafe(service);
    }

    public void checkSafe(LoginType type, String service) {
        StpKit.of(type).checkSafe(service);
    }

    public void closeSafe(LoginType type, String service) {
        StpKit.of(type).closeSafe(service);
    }
}
