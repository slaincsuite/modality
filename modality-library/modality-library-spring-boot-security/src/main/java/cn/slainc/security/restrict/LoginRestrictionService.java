package cn.slainc.security.restrict;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.security.config.SecurityConfigReader;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

public class LoginRestrictionService {

    private final SecurityConfigReader config;

    public LoginRestrictionService(SecurityConfigReader config) {
        this.config = config;
    }

    public void check(LoginType type, String clientIp, String deviceType) {
        List<String> whitelist = config.restrictionIpWhitelist();
        if (!whitelist.isEmpty()) {
            if (clientIp == null || whitelist.stream().noneMatch(prefix -> clientIp.startsWith(prefix))) {
                throw new BizException("登录 IP 不在白名单内，拒绝登录");
            }
            return;
        }
        List<String> blacklist = config.restrictionIpBlacklist();
        if (clientIp != null && blacklist.stream().anyMatch(prefix -> clientIp.startsWith(prefix))) {
            throw new BizException("登录 IP 已被列入黑名单，拒绝登录");
        }
        checkTimeWindow();
        List<String> allowedTypes = config.restrictionAllowedDeviceTypes();
        if (!allowedTypes.isEmpty()) {
            String actual = deviceType == null ? "" : deviceType;
            if (allowedTypes.stream().noneMatch(item -> item.equalsIgnoreCase(actual))) {
                throw new BizException("当前设备类型不允许登录，允许的类型：" + String.join("、", allowedTypes));
            }
        }
    }

    public boolean isTimeAllowed(String timeStart, String timeEnd, LocalTime now) {
        if (timeStart == null || timeStart.isBlank() || timeEnd == null || timeEnd.isBlank()) {
            return true;
        }
        LocalTime start = LocalTime.parse(timeStart.trim());
        LocalTime end = LocalTime.parse(timeEnd.trim());
        if (start.equals(LocalTime.MIDNIGHT) && end.equals(LocalTime.MIDNIGHT)) {
            return true;
        }
        if (start.isBefore(end) || start.equals(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        }
        return !now.isBefore(start) || !now.isAfter(end);
    }

    public void checkTimeWindow() {
        String start = config.restrictionTimeStart();
        String end = config.restrictionTimeEnd();
        if (!isTimeAllowed(start, end, LocalTime.now())) {
            throw new BizException("当前时间不在允许登录的时间段内（" + start + " ~ " + end + "）");
        }
    }

    public String describe() {
        return "时间窗=" + config.restrictionTimeStart() + "~" + config.restrictionTimeEnd()
                + "，IP 白名单=" + config.restrictionIpWhitelist().size()
                + "，IP 黑名单=" + config.restrictionIpBlacklist().size();
    }
}
