package cn.slainc.captcha.support;

import cn.slainc.captcha.Channels;

public final class CaptchaKeys {

    private static final String PREFIX = "modality:captcha:";

    private CaptchaKeys() {
    }

    public static String dataKey(String channel, String id) {
        return PREFIX + "data:" + Channels.normalize(channel) + ":" + id;
    }

    public static String failKey(String channel, String id) {
        return PREFIX + "fail:" + Channels.normalize(channel) + ":" + id;
    }

    public static String lockKey(String channel, String id) {
        return PREFIX + "lock:" + Channels.normalize(channel) + ":" + id;
    }
}
