package cn.slainc.web.ip;

public record UserAgent(String browser, String browserVersion, String os, DeviceType device, boolean bot, String raw) {

    public enum DeviceType {
        PC, MOBILE, TABLET, BOT, UNKNOWN
    }

    public static final UserAgent UNKNOWN = new UserAgent("未知", null, "未知", DeviceType.UNKNOWN, false, null);

    public boolean isMobile() {
        return device == DeviceType.MOBILE;
    }

    public boolean isPc() {
        return device == DeviceType.PC;
    }

    public boolean isBot() {
        return bot;
    }
}
