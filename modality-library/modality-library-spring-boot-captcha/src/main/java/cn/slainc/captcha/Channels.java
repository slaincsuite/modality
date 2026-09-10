package cn.slainc.captcha;

import java.util.Locale;

public final class Channels {

    public static final String WEB = "WEB";
    public static final String APP = "APP";
    public static final String MINI = "MINI";
    public static final String API = "API";

    private Channels() {
    }

    public static String normalize(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("验证码渠道不能为空");
        }
        return channel.trim().toUpperCase(Locale.ROOT);
    }
}
