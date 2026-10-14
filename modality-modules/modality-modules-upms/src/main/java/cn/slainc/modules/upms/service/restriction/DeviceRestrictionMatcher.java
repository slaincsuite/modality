package cn.slainc.modules.upms.service.restriction;

public final class DeviceRestrictionMatcher {

    private DeviceRestrictionMatcher() {
    }

    // 规则：逗号分隔的设备类型（pc/mobile/tablet），空值表示不限制
    public static boolean matches(String allowedDevices, String deviceType) {
        if (allowedDevices == null || allowedDevices.isBlank()) {
            return true;
        }
        if (deviceType == null || deviceType.isBlank()) {
            return false;
        }
        for (String allowed : allowedDevices.split(",")) {
            if (allowed.trim().equalsIgnoreCase(deviceType.trim())) {
                return true;
            }
        }
        return false;
    }
}
